package ch.qscqlmpa.wingtracker.ui.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.backup.BackupException
import ch.qscqlmpa.wingtracker.backup.BackupSummary
import ch.qscqlmpa.wingtracker.backup.CreateBackup
import ch.qscqlmpa.wingtracker.backup.GetBackupStatus
import ch.qscqlmpa.wingtracker.backup.InspectBackup
import ch.qscqlmpa.wingtracker.backup.RestoreBackup
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.FileSaver
import ch.qscqlmpa.wingtracker.ui.toFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime

/** Use cases the backup section needs; passed in so this module never sees the DI framework. */
class BackupUseCases(
    val getBackupStatus: GetBackupStatus,
    val createBackup: CreateBackup,
    val inspectBackup: InspectBackup,
    val restoreBackup: RestoreBackup,
)

/** A backup just made, and where it went. */
data class SavedBackup(
    val path: String,
    val summary: BackupSummary,
)

/** Drives the backup section of the settings: tell how old the last backup is and make a new one (ADR 0031). */
class BackupPresenter(
    private val scope: CoroutineScope,
    private val useCases: BackupUseCases,
    private val fileSaver: FileSaver,
    private val saveTitle: String,
    // Lets anything still waiting (e.g. an undoable delete) reach the stored data, so the backup matches the screen.
    private val beforeBackup: suspend () -> Unit = {},
) {
    var lastMadeAt by mutableStateOf<LocalDateTime?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var saved by mutableStateOf<SavedBackup?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set

    fun load() = attempt(FailureKind.LoadSettings) { lastMadeAt = useCases.getBackupStatus().lastMadeAt }

    fun create() =
        attempt(FailureKind.CreateBackup) {
            saved = null
            val destination =
                fileSaver.choose(saveTitle, useCases.getBackupStatus().suggestedFileName) ?: return@attempt
            busy = true
            try {
                beforeBackup()
                saved = SavedBackup(destination, useCases.createBackup(destination))
                lastMadeAt = useCases.getBackupStatus().lastMadeAt
            } finally {
                busy = false
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
            } catch (e: BackupException) {
                failure = Failure(e.problem.failureKind(), e.message)
                // A full drive or a vanished folder must show a message, never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = e.toFailure(kind)
            }
        }
    }
}
