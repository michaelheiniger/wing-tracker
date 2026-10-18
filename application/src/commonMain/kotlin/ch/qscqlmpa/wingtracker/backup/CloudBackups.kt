package ch.qscqlmpa.wingtracker.backup

import ch.qscqlmpa.wingtracker.settings.SettingsRepository

/** Remembers where backups are uploaded to, or forgets it with null; the secret key is the caller's to keep. */
class SaveCloudStorage(
    private val settings: SettingsRepository,
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
