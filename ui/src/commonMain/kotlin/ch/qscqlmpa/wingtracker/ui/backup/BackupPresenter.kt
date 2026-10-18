package ch.qscqlmpa.wingtracker.ui.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.backup.BackupException
import ch.qscqlmpa.wingtracker.backup.BackupSummary
import ch.qscqlmpa.wingtracker.backup.CloudStorageException
import ch.qscqlmpa.wingtracker.backup.CloudStorageSettings
import ch.qscqlmpa.wingtracker.backup.CreateBackup
import ch.qscqlmpa.wingtracker.backup.GetBackupStatus
import ch.qscqlmpa.wingtracker.backup.InspectBackup
import ch.qscqlmpa.wingtracker.backup.RestoreBackup
import ch.qscqlmpa.wingtracker.backup.UploadBackup
import ch.qscqlmpa.wingtracker.backup.UploadedBackup
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.FileSaver
import ch.qscqlmpa.wingtracker.ui.toFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime

/** Use cases the backup section needs; passed in so this module never sees the DI framework. */
class BackupUseCases(
    val getBackupStatus: GetBackupStatus,
    val createBackup: CreateBackup,
    val inspectBackup: InspectBackup,
    val restoreBackup: RestoreBackup,
    val uploadBackup: UploadBackup,
)

/** A backup just made, and where it went. */
sealed interface BackupOutcome {
    data class Saved(
        val path: String,
        val summary: BackupSummary,
    ) : BackupOutcome

    data class Uploaded(
        val upload: UploadedBackup,
    ) : BackupOutcome
}

/** Drives the backup section of the settings: tell how old the last backup is and make a new one (ADR 0031). */
class BackupPresenter(
    private val scope: CoroutineScope,
    private val useCases: BackupUseCases,
    private val fileSaver: FileSaver,
    private val saveTitle: String,
    private val cloudSecret: CloudSecret,
    // Lets anything still waiting (e.g. an undoable delete) reach the stored data, so the backup matches the screen.
    private val beforeBackup: suspend () -> Unit = {},
    // Lets the reminder to back up go away as soon as the backup is made (ADR 0012 computes it on demand).
    private val onCreated: () -> Unit = {},
) {
    var lastMadeAt by mutableStateOf<LocalDateTime?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Set while a backup is being uploaded, which the pilot may cancel. */
    var uploading by mutableStateOf(false)
        private set
    var outcome by mutableStateOf<BackupOutcome?>(null)
        private set

    /** The bucket backups can be uploaded to, once one is set up (ADR 0032). */
    var cloud by mutableStateOf<CloudStorageSettings?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set

    fun load() =
        attempt(FailureKind.LoadSettings) {
            val status = useCases.getBackupStatus()
            lastMadeAt = status.lastMadeAt
            cloud = status.cloudStorage
        }

    fun create() =
        attempt(FailureKind.CreateBackup) {
            outcome = null
            val destination =
                fileSaver.choose(saveTitle, useCases.getBackupStatus().suggestedFileName) ?: return@attempt
            backingUp { BackupOutcome.Saved(destination, useCases.createBackup(destination)) }
        }

    private var upload: Job? = null

    fun upload() {
        if (busy) return
        upload =
            attempt(FailureKind.UploadBackup) {
                outcome = null
                // A key kept for the session only is gone after a restart; it is typed again under Cloud storage.
                val secretKey = cloudSecret.current() ?: throw MissingSecretException()
                uploading = true
                try {
                    backingUp { BackupOutcome.Uploaded(useCases.uploadBackup(secretKey)) }
                } finally {
                    uploading = false
                }
            }
    }

    /**
     * Stops an upload; nothing is left on this computer and nothing is reported. A cancel that comes too late finds the
     * backup already in the bucket and recorded, so the status is read again afterwards.
     */
    fun cancelUpload() {
        val running = upload ?: return
        if (!uploading) return
        running.cancel()
        scope.launch {
            running.join()
            load()
            onCreated()
        }
    }

    private suspend fun backingUp(backup: suspend () -> BackupOutcome) {
        busy = true
        try {
            beforeBackup()
            outcome = backup()
            lastMadeAt = useCases.getBackupStatus().lastMadeAt
            onCreated()
        } finally {
            busy = false
        }
    }

    private fun attempt(
        kind: FailureKind,
        action: suspend () -> Unit,
    ): Job =
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
                // A full drive or a vanished folder must show a message, never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = e.toFailure(kind)
            }
        }
}

internal class MissingSecretException : Exception("No secret key is kept for the cloud storage")
