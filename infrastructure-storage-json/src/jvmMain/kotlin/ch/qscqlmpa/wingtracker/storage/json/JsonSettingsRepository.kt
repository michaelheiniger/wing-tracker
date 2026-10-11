package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import java.nio.file.Path

class JsonSettingsRepository(
    dataDirectory: Path,
) : SettingsRepository {
    private val store = JsonDocumentStore(dataDirectory)

    /**
     * Settings are preferences, not user data: an unreadable file is set aside and defaults apply,
     * because the application must always be able to start.
     */
    override suspend fun load(): AppSettings {
        val text = store.read(FILE_NAME) ?: return AppSettings()
        return try {
            json.decodeFromString<SettingsDocument>(text).toDomain()
        } catch (_: UnsupportedSchemaException) {
            AppSettings()
        } catch (_: IllegalArgumentException) {
            store.quarantine(FILE_NAME)
            AppSettings()
        } catch (_: IllegalStateException) {
            store.quarantine(FILE_NAME)
            AppSettings()
        }
    }

    override suspend fun save(settings: AppSettings) {
        store.write(FILE_NAME, json.encodeToString(SettingsDocument.from(settings)))
    }

    private companion object {
        const val FILE_NAME = "settings"
    }
}
