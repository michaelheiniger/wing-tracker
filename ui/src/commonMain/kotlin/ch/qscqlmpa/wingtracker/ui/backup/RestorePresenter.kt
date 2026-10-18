package ch.qscqlmpa.wingtracker.ui.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.backup.BackupException
import ch.qscqlmpa.wingtracker.backup.BackupPreview
import ch.qscqlmpa.wingtracker.backup.BackupProblem
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.DataLocation
import ch.qscqlmpa.wingtracker.ui.platform.FilePicker
import ch.qscqlmpa.wingtracker.ui.platform.FolderCheck
import ch.qscqlmpa.wingtracker.ui.toFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Where a restore stands; null on the presenter while none is under way. */
sealed interface RestoreStep {
    /** A checked backup, waiting for the pilot to choose the folder it goes into. */
    data class Confirm(
        val archive: String,
        val preview: BackupPreview,
    ) : RestoreStep

    /** Unpacking the backup [confirm] stands for. */
    data class Restoring(
        val confirm: Confirm,
    ) : RestoreStep

    /** Unpacked into [folder], which is used from the next start (ADR 0020). */
    data class Restored(
        val folder: String,
    ) : RestoreStep
}

/** The dialog titles, given by the screen in the pilot's language. */
class RestoreTitles(
    val pickBackup: String,
    val chooseFolder: String,
)

/**
 * Drives restoring a backup (ADR 0031): check it, unpack it into an empty folder the pilot chooses, then switch to
 * that folder on restart. The current data folder is never written to.
 */
class RestorePresenter(
    private val scope: CoroutineScope,
    private val useCases: BackupUseCases,
    private val filePicker: FilePicker,
    private val location: DataLocation,
    private val titles: RestoreTitles,
    // Lets anything still waiting (e.g. an undoable delete) finish in the current folder before it is left.
    private val beforeRestore: suspend () -> Unit = {},
) {
    val currentFolder: String get() = location.path

    var step by mutableStateOf<RestoreStep?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set

    fun start() =
        attempt {
            val archive = filePicker.pickPath(titles.pickBackup, setOf("zip")) ?: return@attempt
            step = RestoreStep.Confirm(archive, useCases.inspectBackup(archive))
        }

    fun chooseFolder() {
        val confirm = step as? RestoreStep.Confirm ?: return
        attempt {
            val folder = location.chooseFolder(titles.chooseFolder) ?: return@attempt
            folderProblem(location.inspect(folder))?.let {
                failure = Failure(it, folder)
                return@attempt
            }
            step = RestoreStep.Restoring(confirm)
            try {
                beforeRestore()
                useCases.restoreBackup(confirm.archive, folder)
                location.change(folder, moveData = false)
                step = RestoreStep.Restored(folder)
            } finally {
                // A failed restore goes back to the confirmation, so the pilot can pick another folder.
                if (step is RestoreStep.Restoring) step = confirm
            }
        }
    }

    fun cancel() {
        step = null
        failure = null
    }

    fun restart() = location.restart()

    private fun folderProblem(check: FolderCheck): FailureKind? =
        when (check) {
            FolderCheck.Empty -> null
            FolderCheck.HasData -> FailureKind.RestoreFolderNotEmpty
            FolderCheck.Same -> FailureKind.DataFolderSame
            FolderCheck.Unusable -> FailureKind.DataFolderUnusable
            FolderCheck.Overlaps -> FailureKind.DataFolderOverlaps
        }

    private fun attempt(action: suspend () -> Unit) {
        scope.launch {
            try {
                failure = null
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                failure = Failure(e.problem.failureKind(), e.message)
                // A vanished drive or a full disk must show a message, never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = e.toFailure(FailureKind.RestoreBackup)
            }
        }
    }
}

internal fun BackupProblem.failureKind(): FailureKind =
    when (this) {
        BackupProblem.InsideDataFolder -> FailureKind.BackupInsideDataFolder
        BackupProblem.NotABackup -> FailureKind.NotABackup
        BackupProblem.Damaged -> FailureKind.BackupDamaged
        BackupProblem.NewerFormat -> FailureKind.BackupNewerFormat
        BackupProblem.FolderNotEmpty -> FailureKind.RestoreFolderNotEmpty
    }
