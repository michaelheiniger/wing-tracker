package ch.qscqlmpa.wingtracker.backup

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import kotlinx.datetime.LocalDateTime

/** When the last backup was made, in the pilot's time, and the name a new one is offered under. */
data class BackupStatus(
    val lastMadeAt: LocalDateTime?,
    val suggestedFileName: String,
)

class GetBackupStatus(
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(): BackupStatus =
        BackupStatus(
            lastMadeAt = settings.load().lastBackupAt?.let(clock::local),
            // Dated, so backups saved in one folder sort by age and never replace each other by accident.
            suggestedFileName = "wing-tracker-backup-${clock.today()}.zip",
        )
}

/** Backs up all stored data and remembers when, so the pilot can see how old their last backup is (ADR 0031). */
class CreateBackup(
    private val archive: BackupArchive,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val appVersion: String,
) {
    suspend operator fun invoke(destination: String): BackupSummary {
        val now = clock.now()
        val summary = archive.create(destination, appVersion, now)
        // Only once the archive is complete: a failed backup must not look like a recent one.
        settings.save(settings.load().copy(lastBackupAt = now))
        return summary
    }
}
