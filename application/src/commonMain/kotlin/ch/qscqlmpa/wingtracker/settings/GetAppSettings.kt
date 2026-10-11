package ch.qscqlmpa.wingtracker.settings

class GetAppSettings(
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(): AppSettings = settings.load()
}
