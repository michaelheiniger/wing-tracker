package ch.qscqlmpa.wingtracker.backup

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.settings.DeviceSettingsRepository
import ch.qscqlmpa.wingtracker.settings.LogbookSettingsRepository
import ch.qscqlmpa.wingtracker.updates.AppVersion
import kotlinx.datetime.LocalDateTime

/** When the last backup was made, in the pilot's time, and the name a new one is offered under. */
data class BackupStatus(
    val lastMadeAt: LocalDateTime?,
    val suggestedFileName: String,
    // Uploading is offered once a bucket is set up (ADR 0032).
    val cloudStorage: CloudStorageSettings? = null,
)

class GetBackupStatus(
    private val logbookSettings: LogbookSettingsRepository,
    private val deviceSettings: DeviceSettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(): BackupStatus =
        BackupStatus(
            lastMadeAt = deviceSettings.load().lastBackupAt?.let(clock::local),
            // Dated, so backups saved in one folder sort by age and never replace each other by accident.
            suggestedFileName = "wing-tracker-backup-${clock.today()}.zip",
            cloudStorage = logbookSettings.load().cloudStorage,
        )
}

/** Backs up all stored data and remembers when, so the pilot can see how old their last backup is (ADR 0031). */
class CreateBackup(
    private val archive: BackupArchive,
    private val settings: DeviceSettingsRepository,
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

/** A checked backup, as the pilot sees it before restoring it. */
data class BackupPreview(
    val madeAt: LocalDateTime,
    val appVersion: String,
    val summary: BackupSummary,
    // Records only a newer version reads are kept but not shown until updating (ADR 0015).
    val madeByNewerVersion: Boolean,
)

class InspectBackup(
    private val archive: BackupArchive,
    private val clock: AppClock,
    private val appVersion: String,
) {
    suspend operator fun invoke(path: String): BackupPreview {
        val contents = archive.inspect(path)
        val made = AppVersion.parseOrNull(contents.appVersion)
        // A development build has no release version, so it cannot tell whether a backup is newer.
        val installed = AppVersion.parseOrNull(appVersion)
        return BackupPreview(
            madeAt = clock.local(contents.createdAt),
            appVersion = contents.appVersion,
            summary = contents.summary,
            madeByNewerVersion = made != null && installed != null && made > installed,
        )
    }
}

/** Unpacks a backup into a new data folder; switching to it is up to the platform (ADR 0020, ADR 0031). */
class RestoreBackup(
    private val archive: BackupArchive,
) {
    suspend operator fun invoke(
        path: String,
        folder: String,
    ) = archive.restore(path, folder)
}
