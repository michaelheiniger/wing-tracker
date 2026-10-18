package ch.qscqlmpa.wingtracker.ui.syride

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.importing.ImportResult
import ch.qscqlmpa.wingtracker.flights.syride.CompleteSyrideFlights
import ch.qscqlmpa.wingtracker.flights.syride.CountSyrideFlightsWithoutTrack
import ch.qscqlmpa.wingtracker.flights.syride.ImportSyrideFlights
import ch.qscqlmpa.wingtracker.flights.syride.PrepareSyrideImport
import ch.qscqlmpa.wingtracker.flights.syride.SyrideException
import ch.qscqlmpa.wingtracker.flights.syride.SyrideFlight
import ch.qscqlmpa.wingtracker.flights.syride.SyrideImportDraft
import ch.qscqlmpa.wingtracker.flights.syride.SyrideProblem
import ch.qscqlmpa.wingtracker.flights.syride.UpdateSyrideTakeOffTimes
import ch.qscqlmpa.wingtracker.settings.SaveSyrideUsername
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.flights.importing.WingChoice
import ch.qscqlmpa.wingtracker.ui.flights.importing.WingMapping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where the import is: ask for the account, read it, decide about the wings, or show what happened. */
enum class SyrideStage { Account, Reading, Wings, Importing, Done }

/**
 * Drives the import from Syride: read the account, let the pilot say which wing each Syride wing is (or create it),
 * and import. Nothing is saved before the pilot confirms the wings.
 */
class SyrideImportPresenter(
    private val scope: CoroutineScope,
    private val prepare: PrepareSyrideImport,
    private val import: ImportSyrideFlights,
    private val updateTimes: UpdateSyrideTakeOffTimes,
    private val complete: CompleteSyrideFlights,
    private val countWithoutTrack: CountSyrideFlightsWithoutTrack,
    private val saveUsername: SaveSyrideUsername,
    initialUsername: String,
) {
    var username by mutableStateOf(initialUsername)
        private set
    var stage by mutableStateOf(SyrideStage.Account)
        private set
    var draft by mutableStateOf<SyrideImportDraft?>(null)
        private set
    val mapping = WingMapping()
    var result by mutableStateOf<ImportResult<SyrideFlight>?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set

    /** Busy while flights imported earlier get their take-off time. */
    var updatingTimes by mutableStateOf(false)
        private set

    /** How many flights imported earlier got their take-off time; null until the pilot asked for it. */
    var updatedTimes by mutableStateOf<Int?>(null)
        private set

    /** Busy while flights imported earlier get what Syride gives since. */
    var completing by mutableStateOf(false)
        private set

    /** How many flights imported earlier were completed; null until the pilot asked for it. */
    var completed by mutableStateOf<Int?>(null)
        private set

    /** Syride flights in the logbook whose track is not stored yet, counted after reading and after importing. */
    var withoutTrack by mutableStateOf(0)
        private set

    /** The tracks are offered once the flights are imported, or right away when there is nothing new to import. */
    val offersTracks: Boolean
        get() =
            withoutTrack > 0 &&
                (stage == SyrideStage.Done || (stage == SyrideStage.Wings && draft?.groups.isNullOrEmpty() == true))

    /** Take-off times the pilot was offered and has not updated; tracks of these flights cannot match by time yet. */
    val takeOffTimesPending: Boolean
        get() = updatedTimes == null && draft?.takeOffTimeUpdates.orEmpty().isNotEmpty()

    private var reading: Job? = null

    val canRead: Boolean get() = username.isNotBlank() && stage == SyrideStage.Account

    /** Flights that will be imported with the current choices; a new wing that is still incomplete holds back. */
    val importableCount: Int
        get() =
            draft
                ?.groups
                .orEmpty()
                .filter { mapping.isReady(it) }
                .sumOf { it.flights.size }

    val allReady: Boolean
        get() = draft?.groups.orEmpty().all { mapping.choiceOf(it) == WingChoice.Skip || mapping.isReady(it) }

    fun changeUsername(value: String) {
        username = value
    }

    /** Fills in the account used before, unless the pilot already started typing another one. */
    fun suggestUsername(remembered: String?) {
        if (username.isEmpty() && !remembered.isNullOrBlank()) username = remembered
    }

    fun read() {
        val name = username.trim()
        stage = SyrideStage.Reading
        failure = null
        reading =
            scope.launch {
                try {
                    val prepared = prepare(name)
                    saveUsername(name)
                    // Counted now too: with nothing new to import, the tracks are offered straight away.
                    withoutTrack = countFlightsWithoutTrack()
                    start(prepared)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SyrideException) {
                    val detail = e.message.takeIf { e.problem != SyrideProblem.UserNotFound }
                    failure = Failure(e.problem.failureKind(), detail)
                    stage = SyrideStage.Account
                    // Any failure must reach the user as a message; it must never crash the window.
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    failure = Failure(FailureKind.SyrideUnreachable, e.message)
                    stage = SyrideStage.Account
                }
            }
    }

    /** Stops a read that keeps waiting, e.g. on a connection that hangs; the pilot is back at the account step. */
    fun cancelReading() {
        if (stage != SyrideStage.Reading) return
        reading?.cancel()
        stage = SyrideStage.Account
    }

    fun back() {
        stage = SyrideStage.Account
        failure = null
    }

    fun import(onFinished: () -> Unit) {
        val current = draft ?: return
        stage = SyrideStage.Importing
        scope.launch {
            try {
                result = import(mapping.selection(current.groups), mapping.newWings)
                withoutTrack = countFlightsWithoutTrack()
                stage = SyrideStage.Done
                onFinished()
            } catch (e: CancellationException) {
                throw e
                // Any failure must reach the user as a message; it must never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = Failure(FailureKind.ImportSyride, e.message)
                stage = SyrideStage.Wings
            }
        }
    }

    /** Gives flights imported earlier Syride's take-off time; independent of importing new ones. */
    fun updateTakeOffTimes() {
        val updates = draft?.takeOffTimeUpdates.orEmpty()
        if (updates.isEmpty() || updatingTimes) return
        updatingTimes = true
        failure = null
        scope.launch {
            try {
                updatedTimes = updateTimes(updates)
            } catch (e: CancellationException) {
                throw e
                // Any failure must reach the user as a message; it must never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = Failure(FailureKind.UpdateTakeOffTimes, e.message)
            } finally {
                updatingTimes = false
            }
        }
    }

    /** Fills in what Syride gives since on flights imported earlier; independent of importing new ones. */
    fun completeFlights() {
        val completions = draft?.completions.orEmpty()
        if (completions.isEmpty() || completing) return
        completing = true
        failure = null
        scope.launch {
            try {
                completed = complete(completions)
            } catch (e: CancellationException) {
                throw e
                // Any failure must reach the user as a message; it must never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = Failure(FailureKind.CompleteSyrideFlights, e.message)
            } finally {
                completing = false
            }
        }
    }

    // Only decides whether to offer adding the tracks; a failure here must not hide that the import worked.
    private suspend fun countFlightsWithoutTrack(): Int =
        try {
            countWithoutTrack()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") _: Exception,
        ) {
            0
        }

    private fun start(prepared: SyrideImportDraft) {
        updatedTimes = null
        completed = null
        draft = prepared
        mapping.start(prepared.groups)
        stage = SyrideStage.Wings
    }
}

private fun SyrideProblem.failureKind() =
    when (this) {
        SyrideProblem.UserNotFound -> FailureKind.SyrideUserNotFound
        SyrideProblem.NoConnection -> FailureKind.NoConnection
        SyrideProblem.Unreachable -> FailureKind.SyrideUnreachable
        SyrideProblem.PagesChanged -> FailureKind.SyridePagesChanged
    }
