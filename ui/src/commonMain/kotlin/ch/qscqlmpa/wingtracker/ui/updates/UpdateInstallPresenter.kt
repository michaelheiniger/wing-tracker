package ch.qscqlmpa.wingtracker.ui.updates

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.InstallOutcome
import ch.qscqlmpa.wingtracker.ui.platform.UpdateInstaller
import ch.qscqlmpa.wingtracker.updates.AvailableUpdate
import ch.qscqlmpa.wingtracker.updates.DownloadProblem
import ch.qscqlmpa.wingtracker.updates.DownloadResult
import ch.qscqlmpa.wingtracker.updates.DownloadUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where installing an update stands; the install dialog shows one of these at a time. */
sealed interface InstallState {
    data object Idle : InstallState

    data class Downloading(
        val received: Long,
        val total: Long?,
    ) : InstallState {
        /** Null while the size is unknown, so the progress bar shows activity instead of a fraction. */
        val fraction: Float? get() = total?.takeIf { it > 0 }?.let { (received.toDouble() / it).toFloat() }
    }

    // Waiting for the system: its password prompt, then the package manager.
    data object Installing : InstallState

    data object Installed : InstallState

    // Installing replaces the running application, so it happens while the application restarts.
    data object InstallsOnRestart : InstallState

    /** The pilot closed the password prompt; the verified file is kept so trying again needs no new download. */
    data class Cancelled(
        val path: String,
    ) : InstallState

    /** The system cannot install from the app; the pilot installs [path] by hand, with [command] when there is one. */
    data class Manual(
        val path: String,
        val command: String?,
    ) : InstallState

    data class Failed(
        val failure: Failure,
    ) : InstallState
}

/**
 * Downloads, verifies and installs an update, then offers to restart (ADR 0024). Nothing reaches the installer
 * unless its checksum is the published one. [beforeRestart] lets pending work, such as an undoable delete, finish.
 */
class UpdateInstallPresenter(
    private val scope: CoroutineScope,
    private val downloadUpdate: DownloadUpdate,
    private val installer: UpdateInstaller,
    private val beforeRestart: suspend () -> Unit = {},
) {
    var state by mutableStateOf<InstallState>(InstallState.Idle)
        private set

    private var job: Job? = null

    fun install(update: AvailableUpdate) {
        if (state !is InstallState.Idle && state !is InstallState.Failed) return
        state = InstallState.Downloading(0, null)
        job =
            scope.launch {
                guarded {
                    when (val result = downloadUpdate(update) { received, total -> progress(received, total) }) {
                        is DownloadResult.Verified -> handOver(result.path)
                        is DownloadResult.Failed -> state = InstallState.Failed(result.failure())
                    }
                }
            }
    }

    /** Stops a download; the partial file is removed by the download itself. */
    fun cancelDownload() {
        if (state !is InstallState.Downloading) return
        job?.cancel()
        state = InstallState.Idle
    }

    fun retry() {
        val cancelled = state as? InstallState.Cancelled ?: return
        job = scope.launch { guarded { handOver(cancelled.path) } }
    }

    fun showInFolder() {
        val manual = state as? InstallState.Manual ?: return
        scope.launch { guarded { installer.showInFolder(manual.path) } }
    }

    fun restart() {
        scope.launch {
            guarded {
                beforeRestart()
                installer.restart()
            }
        }
    }

    /** Closes the dialog; not while the system is installing, which cannot be stopped halfway. */
    fun close() {
        if (state is InstallState.Installing || state is InstallState.Downloading) return
        state = InstallState.Idle
    }

    private fun progress(
        received: Long,
        total: Long?,
    ) {
        // A cancelled download may still report once; it must not reopen the dialog.
        if (state is InstallState.Downloading) state = InstallState.Downloading(received, total)
    }

    private suspend fun handOver(path: String) {
        state = InstallState.Installing
        state =
            when (val outcome = installer.install(path)) {
                InstallOutcome.Installed -> InstallState.Installed
                InstallOutcome.InstallsOnRestart -> InstallState.InstallsOnRestart
                InstallOutcome.Cancelled -> InstallState.Cancelled(path)
                is InstallOutcome.Manual -> InstallState.Manual(path, outcome.command)
                is InstallOutcome.Failed -> InstallState.Failed(Failure(FailureKind.InstallUpdate, outcome.detail))
            }
    }

    private suspend fun guarded(action: suspend () -> Unit) {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
            // Installing must end in a message, never in a crashed window or a dialog stuck on "Installing".
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            state = InstallState.Failed(Failure(FailureKind.InstallUpdate, e.message))
        }
    }
}

private fun DownloadResult.Failed.failure(): Failure =
    Failure(
        when (problem) {
            DownloadProblem.NoChecksum -> FailureKind.UpdateNoChecksum
            DownloadProblem.ChecksumMismatch -> FailureKind.UpdateChecksumMismatch
            DownloadProblem.NoConnection -> FailureKind.NoConnection
            DownloadProblem.Unreachable -> FailureKind.UpdateUnreachable
            DownloadProblem.Refused -> FailureKind.UpdateRefused
            DownloadProblem.Unexpected -> FailureKind.UpdateUnexpected
        },
        detail,
    )
