package ch.qscqlmpa.wingtracker.settings

interface DeviceSettingsRepository {
    /** Returns defaults when nothing was saved yet, so first launch needs no setup. */
    suspend fun load(): DeviceSettings

    suspend fun save(settings: DeviceSettings)

    /** Saves [settings] as the device settings of the data folder at [folder], which is used from the next start. */
    suspend fun saveFor(
        folder: String,
        settings: DeviceSettings,
    )
}
