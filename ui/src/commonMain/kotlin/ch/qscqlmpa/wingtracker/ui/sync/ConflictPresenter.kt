package ch.qscqlmpa.wingtracker.ui.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.sync.ConflictChoice
import ch.qscqlmpa.wingtracker.sync.SyncConflict
import ch.qscqlmpa.wingtracker.sync.SyncException
import ch.qscqlmpa.wingtracker.sync.SyncReport
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.toFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drives choosing, for each record changed on two computers, which version to keep, then syncing with those
 * choices (ADR 0033). Opened from wherever the conflict shows; the other version stays in the history.
 */
class ConflictPresenter(
    private val scope: CoroutineScope,
    private val useCases: SyncUseCases,
    private val secret: SyncSecret,
) {
    /** Null while the dialog is closed. */
    var conflicts by mutableStateOf<List<SyncConflict>?>(null)
        private set
    var choices by mutableStateOf<Map<SyncedRecord, ConflictChoice>>(emptyMap())
        private set
    var busy by mutableStateOf(false)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set

    /** Whether more collisions turned up since the dialog opened, so the pilot looks again before applying. */
    var moreFound by mutableStateOf(false)
        private set

    val complete: Boolean get() = conflicts?.all { it.record in choices } == true

    private var onResolved: (SyncReport) -> Unit = {}

    fun open(onResolved: (SyncReport) -> Unit) {
        this.onResolved = onResolved
        choices = emptyMap()
        moreFound = false
        attempt(FailureKind.LoadConflicts) { conflicts = useCases.getSyncConflicts() }
    }

    fun choose(
        record: SyncedRecord,
        choice: ConflictChoice,
    ) {
        choices = choices + (record to choice)
    }

    fun apply() {
        if (!complete) return
        attempt(FailureKind.Sync) {
            val token = secret.current()
            if (token == null) {
                failure = Failure(FailureKind.SyncTokenMissing)
                return@attempt
            }
            busy = true
            try {
                val report = useCases.syncLogbook(token, choices)
                if (report.conflicts.isEmpty()) {
                    conflicts = null
                    onResolved(report)
                } else {
                    // The other computer sent more since: the choices made still hold for the records they name.
                    conflicts = useCases.getSyncConflicts()
                    moreFound = true
                }
            } finally {
                busy = false
            }
        }
    }

    fun close() {
        conflicts = null
        failure = null
    }

    private fun attempt(
        kind: FailureKind,
        action: suspend () -> Unit,
    ) = scope.launch {
        try {
            failure = null
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncException) {
            failure = Failure(e.problem.failureKind(), e.message)
            // A broken repository or credential store must show a message, never crash the window.
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            failure = e.toFailure(kind)
        }
    }
}
