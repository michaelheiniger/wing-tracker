package ch.qscqlmpa.wingtracker.settings

interface DeviceSettingsRepository {
    /** Returns defaults when nothing was saved yet, so first launch needs no setup. */
    suspend fun load(): DeviceSettings

    suspend fun save(settings: DeviceSettings)
}
