package ch.qscqlmpa.wingtracker.settings

interface LogbookSettingsRepository {
    /** Returns defaults when nothing was saved yet, so first launch needs no setup. */
    suspend fun load(): LogbookSettings

    suspend fun save(settings: LogbookSettings)
}
