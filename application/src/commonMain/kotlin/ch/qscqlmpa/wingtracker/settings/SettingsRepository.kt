package ch.qscqlmpa.wingtracker.settings

interface SettingsRepository {
    /** Returns defaults when nothing was saved yet, so first launch needs no setup. */
    suspend fun load(): AppSettings

    suspend fun save(settings: AppSettings)
}
