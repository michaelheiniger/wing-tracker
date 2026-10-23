package ch.qscqlmpa.wingtracker.settings

class GetLogbookSettings(
    private val settings: LogbookSettingsRepository,
) {
    suspend operator fun invoke(): LogbookSettings = settings.load()
}

class GetDeviceSettings(
    private val settings: DeviceSettingsRepository,
) {
    suspend operator fun invoke(): DeviceSettings = settings.load()
}
