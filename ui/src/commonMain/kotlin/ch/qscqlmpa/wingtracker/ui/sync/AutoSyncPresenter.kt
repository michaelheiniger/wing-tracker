package ch.qscqlmpa.wingtracker.ui.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.sync.SyncException
import ch.qscqlmpa.wingtracker.sync.SyncProblem
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.toFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where the sync at start stands; the logbook shows once it is [Done]. */
sealed interface StartSync {
    data object Running : StartSync

    /** Waits for the pilot: records changed on two computers, or the sync failed for a reason worth telling. */
    data class Stopped(
        val outcome: SyncOutcome.Conflicts?,
        val failure: Failure?,
    ) : StartSync

    data object Done : StartSync
}

/**
 * Syncs when the app starts, before the logbook shows, and when it closes (ADR 0033). Either can be cut short, and
 * neither keeps the pilot from the app: no connection at start opens the logbook at once, and a close always closes.
 */
class AutoSyncPresenter(
    private val scope: CoroutineScope,
    private val useCases: SyncUseCases,
    private val secret: SyncSecret,
    // What other devices sent may change what the app shows, e.g. the theme; and the start may now go on.
    private val onStarted: (received: Boolean) -> Unit = {},
) {
    var start by mutableStateOf<StartSync>(StartSync.Running)
        private set
    var closing by mutableStateOf(false)
        private set

    private var running: Job? = null
    private var exit: () -> Unit = {}

    // Whether the start received anything, so what was shown before it can be read again.
    private var received = false

    fun startUp() {
        running =
            scope.launch {
                val stopped = syncIfAutomatic()
                if (stopped == null) finishStart() else start = stopped
            }
    }

    /** Opens the logbook without waiting for the sync, which stops. */
    fun skip() {
        running?.cancel()
        finishStart()
    }

    fun continueAfterStop() = finishStart()

    /** Syncs before [exit] when the pilot wants it, and always ends in [exit]. */
    fun close(exit: () -> Unit) {
        if (closing) return
        this.exit = exit
        running =
            scope.launch {
                closing = true
                try {
                    // A conflict or a failure is found again by the next start, which shows it.
                    syncIfAutomatic()
                } finally {
                    exit()
                }
            }
    }

    /** Closes at once; a sync under way stops without leaving anything half-done (ADR 0033). */
    fun closeWithoutSync() {
        running?.cancel()
        exit()
    }

    private fun finishStart() {
        start = StartSync.Done
        onStarted(received)
    }

    /** Null when the sync went through, was not wanted, or found no connection; otherwise why it stopped. */
    private suspend fun syncIfAutomatic(): StartSync.Stopped? =
        try {
            val status = useCases.getSyncStatus()
            // A token kept only for the session is gone after a restart; the pilot types it again on the Sync page.
            val token = secret.current()
            if (!status.automatic || token == null) {
                null
            } else {
                val report = useCases.syncLogbook(token)
                received = report.received > 0
                if (report.conflicts.isEmpty()) {
                    null
                } else {
                    StartSync.Stopped(
                        SyncOutcome.Conflicts(report.conflicts),
                        null,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncException) {
            // Normal at a launch site, so not worth stopping for.
            if (e.problem ==
                SyncProblem.NoConnection
            ) {
                null
            } else {
                StartSync.Stopped(null, Failure(e.problem.failureKind(), e.message))
            }
            // A broken settings file or credential store must not keep the app from opening.
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            StartSync.Stopped(null, e.toFailure(FailureKind.Sync))
        }
}
