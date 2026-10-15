package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

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
        } catch (e: UnsupportedSchemaException) {
            logger.info { "Settings are from a newer version, defaults apply: ${e.message}" }
            AppSettings()
        } catch (e: IllegalArgumentException) {
            logger.warn(e) { "Settings could not be read, defaults apply" }
            store.quarantine(FILE_NAME)
            AppSettings()
        } catch (e: IllegalStateException) {
            logger.warn(e) { "Settings could not be read, defaults apply" }
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
