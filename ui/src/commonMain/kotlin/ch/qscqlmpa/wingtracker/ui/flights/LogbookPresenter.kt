package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.DeleteFlight
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.GetLogbook
import ch.qscqlmpa.wingtracker.flights.Logbook
import ch.qscqlmpa.wingtracker.gear.maintenance.ReadAttachment
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.FileOpener
import ch.qscqlmpa.wingtracker.ui.toDisplay
import ch.qscqlmpa.wingtracker.ui.undo.UndoCoordinator
import ch.qscqlmpa.wingtracker.ui.undo.UndoText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

data class LogbookState(
    val logbook: Logbook? = null,
    val unreadable: List<UnreadableRecord> = emptyList(),
    val failure: Failure? = null,
    // Deleted flights wait here until the pilot can no longer undo, so Undo only has to show them again.
    val hiddenFlights: Set<FlightId> = emptySet(),
) {
    val loading: Boolean get() = logbook == null && failure == null

    /** The logbook as the pilot should see it: without the flights that are about to be deleted. */
    val shown: Logbook? get() = logbook?.without(hiddenFlights)

    /**
     * Date defaults to today and the wing to the one flown last, because a pilot usually logs a flight
     * right after landing, on the same wing as the previous one.
     */
    fun newFlightInput(today: LocalDate): FlightFormInput {
        val wings = logbook?.wings.orEmpty()
        val lastFlown =
            shown
                ?.months
                ?.flatMap { it.flights }
                ?.maxByOrNull { it.loggedAt }
                ?.details
                ?.wingId
        val wingId =
            lastFlown?.takeIf { id -> wings.any { it.id == id } }
                ?: wings.firstOrNull { it.isActive }?.id
                ?: wings.firstOrNull()?.id
        return FlightFormInput(date = today.toDisplay(), wingId = wingId)
    }
}

class LogbookPresenter(
    private val scope: CoroutineScope,
    private val getLogbook: GetLogbook,
    private val deleteFlight: DeleteFlight,
    private val readAttachment: ReadAttachment,
    private val fileOpener: FileOpener,
) {
    var state by mutableStateOf(LogbookState())
        private set

    fun refresh() = run(FailureKind.LoadLogbook) { }

    /** Hides the flight at once and deletes it for real only when the pilot can no longer undo. */
    fun delete(
        id: FlightId,
        undo: UndoCoordinator,
        text: UndoText,
    ) {
        state = state.copy(hiddenFlights = state.hiddenFlights + id)
        scope.launch {
            undo.offer(
                text,
                onUndo = { unhide(id) },
                onExpire = {
                    perform(FailureKind.DeleteFlight) { deleteFlight(id) }
                    unhide(id)
                },
            )
        }
    }

    private fun unhide(id: FlightId) {
        state = state.copy(hiddenFlights = state.hiddenFlights - id)
    }

    /** Opens the imported track in whatever program the system associates with it. */
    fun openTrack(flight: Flight) {
        val igc = flight.igcFile ?: return
        scope.launch {
            try {
                fileOpener.open(igc.attachment.fileName, readAttachment(igc.attachment.id))
            } catch (e: CancellationException) {
                throw e
                // A missing file or viewer must show a message, never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                state = state.copy(failure = Failure(FailureKind.OpenTrack, e.message))
            }
        }
    }

    fun dismissUnreadable() {
        state = state.copy(unreadable = emptyList())
    }

    private fun run(
        kind: FailureKind,
        action: suspend () -> Unit,
    ) {
        scope.launch { perform(kind, action) }
    }

    private suspend fun perform(
        kind: FailureKind,
        action: suspend () -> Unit,
    ) {
        state =
            try {
                action()
                val logbook = getLogbook()
                state.copy(
                    logbook = logbook,
                    // A later reload must not re-announce a quarantined file that was already reported.
                    unreadable = (state.unreadable + logbook.unreadable).distinct(),
                    failure = null,
                )
            } catch (e: CancellationException) {
                throw e
                // Any storage failure must reach the user as a message; it must never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                state.copy(failure = Failure(kind, e.message))
            }
    }
}

private fun Logbook.without(hidden: Set<FlightId>): Logbook {
    if (hidden.isEmpty()) return this
    val removed = months.flatMap { it.flights }.filter { it.id in hidden }
    return copy(
        months =
            months
                .map {
                    it.copy(
                        flights =
                            it.flights.filter { flight ->
                                flight.id !in hidden
                            },
                    )
                }.filter { it.flights.isNotEmpty() },
        hoursByWing =
            hoursByWing.mapValues { (wingId, hours) ->
                hours -
                    removed.filter { it.details.wingId == wingId }.fold(Duration.ZERO) { sum, f ->
                        sum +
                            f.details.duration
                    }
            },
    )
}
