package ch.qscqlmpa.wingtracker.backup

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.settings.DeviceSettingsRepository
import ch.qscqlmpa.wingtracker.settings.LogbookSettingsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime

/** Remembers where backups are uploaded to, or forgets it with null; the secret key is the caller's to keep. */
class SaveCloudStorage(
    private val settings: LogbookSettingsRepository,
) {
    suspend operator fun invoke(cloud: CloudStorageSettings?) {
        settings.save(settings.load().copy(cloudStorage = cloud))
    }
}

/** Tries the bucket with these keys before anything is uploaded, so a typo shows at once (ADR 0032). */
class CheckCloudStorage(
    private val storage: CloudStorage,
) {
    suspend operator fun invoke(target: CloudTarget) = storage.check(target)
}

// Every backup sits below this prefix, so the bucket can hold other things and only backups are ever deleted.
const val CLOUD_BACKUP_PREFIX = "wing-tracker/"
private val BACKUP_NAME = Regex("""wing-tracker-backup-\d{4}-\d{2}-\d{2}-\d{4}\.zip""")

/** A backup in the bucket, named after when it was made in the pilot's time, so the names sort by age. */
private fun backupKey(at: LocalDateTime): String {
    fun two(value: Int) = value.toString().padStart(2, '0')
    return "${CLOUD_BACKUP_PREFIX}wing-tracker-backup-${at.date}-${two(at.hour)}${two(at.minute)}.zip"
}

/** Whether [key] names a backup this application uploaded, the only objects it may delete. */
fun isUploadedBackup(key: String): Boolean =
    key.startsWith(CLOUD_BACKUP_PREFIX) && BACKUP_NAME.matches(key.removePrefix(CLOUD_BACKUP_PREFIX))

/** An upload that went through; deleting older backups may still have failed with [cleanupProblem]. */
data class UploadedBackup(
    val bucket: String,
    val key: String,
    val summary: BackupSummary,
    val deleted: Int,
    val cleanupProblem: CloudProblem?,
)

/**
 * Backs up all stored data straight to the bucket (ADR 0032): the archive passes through a temporary file that is
 * removed whatever happens, and the oldest backups beyond the number to keep are deleted only after the upload.
 */
class UploadBackup(
    private val archive: BackupArchive,
    private val storage: CloudStorage,
    private val logbookSettings: LogbookSettingsRepository,
    private val deviceSettings: DeviceSettingsRepository,
    private val clock: AppClock,
    private val appVersion: String,
) {
    suspend operator fun invoke(secretKey: String): UploadedBackup {
        val cloud = checkNotNull(logbookSettings.load().cloudStorage) { "No cloud storage is set up" }
        val target = cloud.target(secretKey)
        val now = clock.now()
        val key = backupKey(clock.local(now))
        val file = archive.temporaryFile(key.substringAfterLast('/'))
        val summary =
            try {
                archive.create(file, appVersion, now).also { storage.upload(target, key, file) }
            } finally {
                // Also when the pilot cancelled, which would otherwise cancel the removal too.
                withContext(NonCancellable) { archive.discard(file) }
            }
        // The backup is in the bucket: a cancel arriving now must not leave it unrecorded or half tidied up.
        return withContext(NonCancellable) {
            deviceSettings.save(deviceSettings.load().copy(lastBackupAt = now))
            try {
                UploadedBackup(cloud.bucket, key, summary, deleteOldest(target, cloud.keepLast), cleanupProblem = null)
            } catch (e: CloudStorageException) {
                // The backup is safe in the bucket; failing to tidy up must not make it look lost.
                UploadedBackup(cloud.bucket, key, summary, deleted = 0, cleanupProblem = e.problem)
            }
        }
    }

    private suspend fun deleteOldest(
        target: CloudTarget,
        keepLast: Int?,
    ): Int {
        if (keepLast == null) return 0
        val backups = storage.list(target, CLOUD_BACKUP_PREFIX).filter { isUploadedBackup(it.key) }
        val beyond = backups.sortedByDescending { it.lastModified }.drop(keepLast)
        beyond.forEach { storage.delete(target, it.key) }
        return beyond.size
    }
}

/** A backup in the bucket, as the pilot chooses one to restore. */
data class CloudBackup(
    val key: String,
    val madeAt: LocalDateTime,
    val bytes: Long,
)

/** The backups this application uploaded, newest first, in the pilot's time. */
class ListCloudBackups(
    private val storage: CloudStorage,
    private val settings: LogbookSettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(secretKey: String): List<CloudBackup> {
        val cloud = checkNotNull(settings.load().cloudStorage) { "No cloud storage is set up" }
        return storage
            .list(cloud.target(secretKey), CLOUD_BACKUP_PREFIX)
            .filter { isUploadedBackup(it.key) }
            .sortedByDescending { it.lastModified }
            .map { CloudBackup(it.key, clock.local(it.lastModified), it.size) }
    }
}

/**
 * Downloads a backup into a temporary file for the restore of ADR 0031, which checks it like any other. The caller
 * discards the file once done with it; a failed download leaves nothing behind.
 */
class DownloadCloudBackup(
    private val storage: CloudStorage,
    private val archive: BackupArchive,
    private val settings: LogbookSettingsRepository,
) {
    suspend operator fun invoke(
        secretKey: String,
        key: String,
    ): String {
        val cloud = checkNotNull(settings.load().cloudStorage) { "No cloud storage is set up" }
        require(isUploadedBackup(key)) { "Not a backup of this application: $key" }
        val file = archive.temporaryFile(key.substringAfterLast('/'))
        var complete = false
        try {
            storage.download(cloud.target(secretKey), key, file)
            complete = true
            return file
        } finally {
            // Also when the pilot cancelled, which would otherwise cancel the removal too.
            if (!complete) withContext(NonCancellable) { archive.discard(file) }
        }
    }
}

class DiscardTemporaryBackup(
    private val archive: BackupArchive,
) {
    suspend operator fun invoke(path: String) = archive.discard(path)
}
