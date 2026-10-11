package ch.qscqlmpa.wingtracker.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.DataLocation
import ch.qscqlmpa.wingtracker.ui.platform.FolderCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A folder the pilot picked, waiting for them to say what to do with the current data. */
data class FolderProposal(
    val folder: String,
    // Moving needs an empty folder; one that already holds data (a restored backup) is used as it is.
    val canMove: Boolean,
)

/** Drives the data folder section of the settings: show, open and change the folder the data lives in. */
class DataLocationPresenter(
    private val scope: CoroutineScope,
    private val location: DataLocation,
    private val chooseTitle: String,
    // Lets anything still waiting (e.g. an undoable delete) finish in the old folder before it is left.
    private val beforeChange: suspend () -> Unit = {},
) {
    val path: String get() = location.path
    val isDefault: Boolean get() = location.isDefault

    var proposal by mutableStateOf<FolderProposal?>(null)
        private set
    var restartRequired by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set

    fun open() = attempt(FailureKind.OpenDataFolder) { location.open() }

    fun choose() =
        attempt(FailureKind.ChangeDataFolder) {
            location.chooseFolder(chooseTitle)?.let { propose(it) }
        }

    fun useDefault() = attempt(FailureKind.ChangeDataFolder) { propose(location.defaultPath) }

    fun cancel() {
        proposal = null
    }

    fun confirm(moveData: Boolean) {
        val chosen = proposal ?: return
        proposal = null
        busy = true
        attempt(FailureKind.ChangeDataFolder) {
            try {
                beforeChange()
                location.change(chosen.folder, moveData)
                restartRequired = true
            } finally {
                busy = false
            }
        }
    }

    fun restart() = location.restart()

    fun dismissFailure() {
        failure = null
    }

    private suspend fun propose(folder: String) {
        when (location.inspect(folder)) {
            FolderCheck.Empty -> proposal = FolderProposal(folder, canMove = true)
            FolderCheck.HasData -> proposal = FolderProposal(folder, canMove = false)
            FolderCheck.Same -> failure = Failure(FailureKind.DataFolderSame)
            FolderCheck.Unusable -> failure = Failure(FailureKind.DataFolderUnusable)
            FolderCheck.Overlaps -> failure = Failure(FailureKind.DataFolderOverlaps)
        }
    }

    private fun attempt(
        kind: FailureKind,
        action: suspend () -> Unit,
    ) {
        scope.launch {
            try {
                failure = null
                action()
            } catch (e: CancellationException) {
                throw e
                // A folder or file-manager problem must show a message, never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = Failure(kind, e.message)
            }
        }
    }
}
