package ch.qscqlmpa.wingtracker.ui.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.backup.BackupException
import ch.qscqlmpa.wingtracker.backup.BackupPreview
import ch.qscqlmpa.wingtracker.backup.BackupProblem
import ch.qscqlmpa.wingtracker.backup.CloudBackup
import ch.qscqlmpa.wingtracker.backup.CloudStorageException
import ch.qscqlmpa.wingtracker.backup.DiscardTemporaryBackup
import ch.qscqlmpa.wingtracker.backup.DownloadCloudBackup
import ch.qscqlmpa.wingtracker.backup.ListCloudBackups
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
    /** The backups in the bucket, waiting for the pilot to choose one (ADR 0032). */
    data class ChooseCloud(
        val backups: List<CloudBackup>,
    ) : RestoreStep

    data class Downloading(
        val backup: CloudBackup,
    ) : RestoreStep

    /**
     * A checked backup, waiting for the pilot to choose the folder it goes into. A [downloaded] archive is a
     * temporary file, removed once the restore is over either way.
     */
    data class Confirm(
        val archive: String,
        val preview: BackupPreview,
        val downloaded: Boolean = false,
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

/** Use cases restoring from the bucket needs; passed in so this module never sees the DI framework. */
class CloudRestoreUseCases(
    val listCloudBackups: ListCloudBackups,
    val downloadCloudBackup: DownloadCloudBackup,
    val discardTemporaryBackup: DiscardTemporaryBackup,
)

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
    private val cloudUseCases: CloudRestoreUseCases,
    private val cloudSecret: CloudSecret,
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

    fun startFromCloud() =
        attempt {
            step = RestoreStep.ChooseCloud(cloudUseCases.listCloudBackups(secretKey()))
        }

    fun chooseCloudBackup(backup: CloudBackup) {
        val choosing = step as? RestoreStep.ChooseCloud ?: return
        attempt {
            step = RestoreStep.Downloading(backup)
            var archive: String? = null
            try {
                archive = cloudUseCases.downloadCloudBackup(secretKey(), backup.key)
                step = RestoreStep.Confirm(archive, useCases.inspectBackup(archive), downloaded = true)
            } finally {
                // A download that fails or turns out damaged goes back to the list, and leaves nothing behind.
                if (step is RestoreStep.Downloading) {
                    step = choosing
                    archive?.let { cloudUseCases.discardTemporaryBackup(it) }
                }
            }
        }
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
                discardDownloaded(confirm)
            } finally {
                // A failed restore goes back to the confirmation, so the pilot can pick another folder.
                if (step is RestoreStep.Restoring) step = confirm
            }
        }
    }

    fun cancel() {
        (step as? RestoreStep.Confirm)?.let { confirm -> scope.launch { discardDownloaded(confirm) } }
        step = null
        failure = null
    }

    // The restore is over; a temporary download serves no purpose any more, and failing to remove it is harmless.
    private suspend fun discardDownloaded(confirm: RestoreStep.Confirm) {
        if (confirm.downloaded) runCatching { cloudUseCases.discardTemporaryBackup(confirm.archive) }
    }

    // A key kept for the session only is gone after a restart; it is typed again under Cloud storage.
    private suspend fun secretKey(): String = cloudSecret.current() ?: throw MissingSecretException()

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
            } catch (e: CloudStorageException) {
                failure = Failure(e.problem.failureKind(), e.message)
            } catch (_: MissingSecretException) {
                failure = Failure(FailureKind.CloudSecretMissing)
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
