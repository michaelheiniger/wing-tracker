package ch.qscqlmpa.wingtracker.ui.syride

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.importing.ImportResult
import ch.qscqlmpa.wingtracker.flights.syride.CorrectSyrideImports
import ch.qscqlmpa.wingtracker.flights.syride.ImportSyrideFlights
import ch.qscqlmpa.wingtracker.flights.syride.PrepareSyrideImport
import ch.qscqlmpa.wingtracker.flights.syride.SyrideException
import ch.qscqlmpa.wingtracker.flights.syride.SyrideFlight
import ch.qscqlmpa.wingtracker.flights.syride.SyrideImportDraft
import ch.qscqlmpa.wingtracker.flights.syride.SyrideProblem
import ch.qscqlmpa.wingtracker.settings.SaveSyrideUsername
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.flights.importing.WingChoice
import ch.qscqlmpa.wingtracker.ui.flights.importing.WingMapping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
    private val correct: CorrectSyrideImports,
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

    /** Busy while earlier flights are being corrected. */
    var correcting by mutableStateOf(false)
        private set

    /** How many earlier flights were corrected; null until the pilot asked for it. */
    var corrected by mutableStateOf<Int?>(null)
        private set

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
        scope.launch {
            try {
                val prepared = prepare(name)
                saveUsername(name)
                start(prepared)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SyrideException) {
                failure = Failure(e.problem.failureKind(), e.message.takeIf { e.problem != SyrideProblem.UserNotFound })
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

    /** Corrects the flights an earlier import garbled; independent of importing new ones. */
    fun correct() {
        val corrections = draft?.corrections.orEmpty()
        if (corrections.isEmpty() || correcting) return
        correcting = true
        failure = null
        scope.launch {
            try {
                corrected = correct(corrections)
            } catch (e: CancellationException) {
                throw e
                // Any failure must reach the user as a message; it must never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = Failure(FailureKind.CorrectSyride, e.message)
            } finally {
                correcting = false
            }
        }
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

    private fun start(prepared: SyrideImportDraft) {
        draft = prepared
        corrected = null
        mapping.start(prepared.groups)
        stage = SyrideStage.Wings
    }
}

private fun SyrideProblem.failureKind() =
    when (this) {
        SyrideProblem.UserNotFound -> FailureKind.SyrideUserNotFound
        SyrideProblem.Unreachable -> FailureKind.SyrideUnreachable
        SyrideProblem.PagesChanged -> FailureKind.SyridePagesChanged
    }
