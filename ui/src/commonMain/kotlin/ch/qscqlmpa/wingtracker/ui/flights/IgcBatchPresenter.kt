package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.FlightDateInFutureException
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.igc.AttachIgcTrack
import ch.qscqlmpa.wingtracker.flights.igc.IgcImportDraft
import ch.qscqlmpa.wingtracker.flights.igc.ImportIgcFlight
import ch.qscqlmpa.wingtracker.flights.igc.PrepareIgcImport
import ch.qscqlmpa.wingtracker.flights.igc.TrackMatch
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Why a track is not (or not yet) imported; the dialog turns each kind into text. */
enum class IgcRowProblem {
    Unreadable,
    NoFlightFound,
    AlreadyImported,
    RepeatedInSelection,
    DateInFuture,
    SaveFailed,

    // Nothing in the logbook matches, on a day that has flights: most likely a recording that was not a flight.
    NoMatchingFlight,

    // The flight this track belongs to has another track, which is never replaced (ADR 0026).
    FlightHasTrack,
}

/** How sure the review is about the flight a track belongs to; the summary counts and filters rows by it. */
enum class TrackMatchKind { Exact, Possible, None }

// Problems that arise while importing; the row stays ticked so the pilot can fix the cause and try again.
private val IMPORT_FAILURES =
    setOf(IgcRowProblem.DateInFuture, IgcRowProblem.SaveFailed)

/**
 * One track of the batch. [wingId] is null while the row follows the dialog's wing for all tracks. [attach] says the
 * track goes to the matching flight instead of creating a new one.
 */
data class IgcBatchRow(
    val file: NewAttachment,
    val draft: IgcImportDraft?,
    val problem: IgcRowProblem?,
    val detail: String? = null,
    val selected: Boolean = problem == null,
    val wingId: WingId? = null,
    val attach: Boolean = draft?.match is TrackMatch.Exact && problem == null,
) {
    // Without a duration there is no flight to log; the pilot logs such a flight by hand instead.
    val selectable: Boolean get() = draft?.duration != null
    val failedToImport: Boolean get() = problem in IMPORT_FAILURES
    val match: TrackMatch? get() = draft?.match

    // A flight that has a track keeps it, so the track can only become a flight of its own.
    val canAttach: Boolean
        get() = match?.let { it.flight.igcFile == null } == true && problem != IgcRowProblem.FlightHasTrack

    // The track becomes a flight of its own, so its wing and the wing's dates matter.
    val createsFlight: Boolean get() = selectable && !attach

    val matchKind: TrackMatchKind
        get() =
            when (match) {
                is TrackMatch.Exact -> TrackMatchKind.Exact
                is TrackMatch.Possible -> TrackMatchKind.Possible
                null -> TrackMatchKind.None
            }
}

fun List<IgcBatchRow>.countOf(kind: TrackMatchKind): Int = count { it.matchKind == kind }

/**
 * Drives the import of several tracks at once. Every track is read first and shown for review; nothing is saved
 * until the pilot confirms, and tracks that fail do not stop the others.
 */
class IgcBatchPresenter(
    private val scope: CoroutineScope,
    private val prepareIgcImport: PrepareIgcImport,
    private val importIgcFlight: ImportIgcFlight,
    private val attachIgcTrack: AttachIgcTrack,
    defaultWingId: WingId,
    // A logger holds every track ever recorded; listing the known ones again on each import would bury the new ones.
    private val hideAlreadyImported: Boolean = false,
) {
    var rows by mutableStateOf<List<IgcBatchRow>>(emptyList())
        private set
    var defaultWingId by mutableStateOf(defaultWingId)
        private set
    var loading by mutableStateOf(true)
        private set
    var importing by mutableStateOf(false)
        private set
    var importedCount by mutableStateOf(0)
        private set
    var skippedCount by mutableStateOf(0)
        private set

    /** Only rows of this kind are shown; null shows them all. */
    var filter by mutableStateOf<TrackMatchKind?>(null)
        private set

    val visibleRows: List<IgcBatchRow> get() = filter?.let { kind -> rows.filter { it.matchKind == kind } } ?: rows

    fun showOnly(kind: TrackMatchKind?) {
        filter = kind
    }

    fun choose(
        row: IgcBatchRow,
        attach: Boolean,
    ) = update(row) { if (it.canAttach || !attach) it.copy(attach = attach) else it }

    val importableCount: Int get() = rows.count { it.selected && it.selectable }
    val failedCount: Int get() = rows.count { it.failedToImport }

    fun wingOf(row: IgcBatchRow): WingId = row.wingId ?: defaultWingId

    fun load(files: List<NewAttachment>) {
        scope.launch {
            val prepared = prepare(files)
            val skipped =
                if (hideAlreadyImported) {
                    prepared.filter {
                        it.problem == IgcRowProblem.AlreadyImported
                    }
                } else {
                    emptyList()
                }
            skippedCount = skipped.size
            rows = prepared - skipped.toSet()
            loading = false
        }
    }

    fun changeDefaultWing(wingId: WingId) {
        defaultWingId = wingId
    }

    fun changeWing(
        row: IgcBatchRow,
        wingId: WingId,
    ) = update(row) { it.copy(wingId = wingId) }

    fun select(
        row: IgcBatchRow,
        selected: Boolean,
    ) = update(row) { if (it.selectable) it.copy(selected = selected) else it }

    /** [onDone] runs when nothing is left to fix, so the dialog can close itself. */
    fun import(onDone: () -> Unit) {
        importing = true
        scope.launch {
            try {
                rows =
                    rows.flatMap { row ->
                        if (row.selected &&
                            row.selectable
                        ) {
                            listOfNotNull(importOne(row))
                        } else {
                            listOf(row)
                        }
                    }
            } finally {
                importing = false
            }
            if (failedCount == 0) onDone()
        }
    }

    private fun update(
        row: IgcBatchRow,
        change: (IgcBatchRow) -> IgcBatchRow,
    ) {
        rows = rows.map { if (it === row) change(it) else it }
    }

    private suspend fun prepare(files: List<NewAttachment>): List<IgcBatchRow> {
        val seen = mutableSetOf<String>()
        // A track downloaded from Syride and the logger's own file of the same flight can come in one selection.
        val claimed = mutableSetOf<FlightId>()
        return files.map { file ->
            try {
                val draft = prepareIgcImport(file)
                // The same file twice in one selection would otherwise slip past the check against stored flights.
                val firstInSelection = seen.add(draft.checksum)
                val exact = draft.match as? TrackMatch.Exact
                val problem =
                    when {
                        draft.duration == null -> {
                            IgcRowProblem.NoFlightFound
                        }

                        draft.duplicateOf != null -> {
                            IgcRowProblem.AlreadyImported
                        }

                        !firstInSelection -> {
                            IgcRowProblem.RepeatedInSelection
                        }

                        exact != null && (exact.flight.igcFile != null || !claimed.add(exact.flight.id)) -> {
                            IgcRowProblem.FlightHasTrack
                        }

                        draft.match == null && draft.dayHasFlights -> {
                            IgcRowProblem.NoMatchingFlight
                        }

                        else -> {
                            null
                        }
                    }
                IgcBatchRow(file, draft, problem)
            } catch (e: CancellationException) {
                throw e
                // A file that cannot be read must show a message on its row, never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                IgcBatchRow(file, null, IgcRowProblem.Unreadable, e.message)
            }
        }
    }

    /** Returns null when the track was imported, otherwise the row marked with what went wrong. */
    private suspend fun importOne(row: IgcBatchRow): IgcBatchRow? {
        val draft = checkNotNull(row.draft)
        return try {
            val target = row.match?.flight
            if (row.attach && target != null) {
                attachIgcTrack(target.id, row.file, draft.checksum)
            } else {
                val details =
                    FlightDetails(
                        draft.date,
                        checkNotNull(draft.duration),
                        wingOf(row),
                        takeOffTime = draft.takeOffTime,
                    )
                importIgcFlight(details, row.file, draft.checksum)
            }
            importedCount++
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: FlightDateInFutureException) {
            row.copy(problem = IgcRowProblem.DateInFuture, detail = null)
            // Any storage failure must reach the user as a message on its row; it must never crash the window.
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            row.copy(problem = IgcRowProblem.SaveFailed, detail = e.message)
        }
    }
}
