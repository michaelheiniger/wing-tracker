package ch.qscqlmpa.wingtracker.ui.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.sync.JoinLogbook
import ch.qscqlmpa.wingtracker.sync.SyncException
import ch.qscqlmpa.wingtracker.sync.SyncSettings
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.backup.freeFolderProblem
import ch.qscqlmpa.wingtracker.ui.platform.DataLocation
import ch.qscqlmpa.wingtracker.ui.toFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where joining a logbook stands; null on the presenter while none is offered. */
sealed interface JoinStep {
    /** The repository holds a logbook: the pilot may use it on this device, from a folder they choose. */
    data class Offered(
        val settings: SyncSettings,
        val token: String,
    ) : JoinStep

    data class Copying(
        val offered: Offered,
    ) : JoinStep

    /** Copied into [folder], which is used from the next start (ADR 0020). */
    data class Joined(
        val folder: String,
    ) : JoinStep
}

/**
 * Drives using on this device the logbook a repository already holds (ADR 0033): copy it into an empty folder the
 * pilot chooses, then switch to that folder on restart. The current data folder is never written to.
 */
class JoinPresenter(
    private val scope: CoroutineScope,
    private val joinLogbook: JoinLogbook,
    private val location: DataLocation,
    private val secret: SyncSecret,
    private val chooseFolderTitle: String,
    // Lets anything still waiting (e.g. an undoable delete) finish in the current folder before it is left.
    private val beforeJoin: suspend () -> Unit = {},
) {
    val currentFolder: String get() = location.path

    var step by mutableStateOf<JoinStep?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set

    private var copying: Job? = null

    fun offer(
        settings: SyncSettings,
        token: String,
    ) {
        step = JoinStep.Offered(settings, token)
        failure = null
    }

    fun chooseFolder() {
        val offered = step as? JoinStep.Offered ?: return
        copying =
            scope.launch {
                try {
                    failure = null
                    val folder = location.chooseFolder(chooseFolderTitle) ?: return@launch
                    location.inspect(folder).freeFolderProblem()?.let {
                        failure = Failure(it, folder)
                        return@launch
                    }
                    copy(offered, folder)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SyncException) {
                    failure = Failure(e.problem.failureKind(), e.message)
                    // A vanished drive or a full disk must show a message, never crash the window.
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    failure = e.toFailure(FailureKind.JoinLogbook)
                }
            }
    }

    /** Closes the offer, and stops a copy still running. */
    fun cancel() {
        copying?.cancel()
        step = null
        failure = null
    }

    fun restart() = location.restart()

    private suspend fun copy(
        offered: JoinStep.Offered,
        folder: String,
    ) {
        step = JoinStep.Copying(offered)
        try {
            beforeJoin()
            joinLogbook(offered.settings, offered.token, folder)
            secret.keep(offered.token)
            location.change(folder, moveData = false)
            step = JoinStep.Joined(folder)
        } finally {
            // A copy that failed or was cancelled goes back to the offer, so the pilot can pick another folder.
            if (step is JoinStep.Copying) step = offered
        }
    }
}
