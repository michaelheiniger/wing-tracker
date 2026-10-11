package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.ThemeMode
import kotlinx.serialization.Serializable

@Serializable
internal data class SettingsDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val themeMode: String = ThemeMode.System.name,
) {
    fun toDomain(): AppSettings {
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw UnsupportedSchemaException("Settings were written by a newer version of the application")
        }
        return AppSettings(themeMode = ThemeMode.valueOf(themeMode))
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun from(settings: AppSettings) = SettingsDocument(themeMode = settings.themeMode.name)
    }
}
