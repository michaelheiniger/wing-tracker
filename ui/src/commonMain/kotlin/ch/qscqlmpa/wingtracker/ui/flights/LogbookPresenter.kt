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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class LogbookState(
    val logbook: Logbook? = null,
    val unreadable: List<UnreadableRecord> = emptyList(),
    val failure: Failure? = null,
) {
    val loading: Boolean get() = logbook == null && failure == null

    /**
     * Date defaults to today and the wing to the one flown last, because a pilot usually logs a flight
     * right after landing, on the same wing as the previous one.
     */
    fun newFlightInput(today: LocalDate): FlightFormInput {
        val wings = logbook?.wings.orEmpty()
        val lastFlown =
            logbook
                ?.months
                ?.flatMap { it.flights }
                ?.maxByOrNull { it.loggedAt }
                ?.details
                ?.wingId
        val wingId =
            lastFlown?.takeIf { id -> wings.any { it.id == id } }
                ?: wings.firstOrNull { it.isActive }?.id
                ?: wings.firstOrNull()?.id
        return FlightFormInput(date = today, wingId = wingId)
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

    fun delete(id: FlightId) = run(FailureKind.DeleteFlight) { deleteFlight(id) }

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
        scope.launch {
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
}
