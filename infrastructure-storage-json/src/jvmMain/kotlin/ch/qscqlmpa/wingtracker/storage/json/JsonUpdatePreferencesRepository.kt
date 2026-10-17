package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.updates.UpdatePreferences
import ch.qscqlmpa.wingtracker.updates.UpdatePreferencesRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/** Kept in a folder of this installation, not in the data folder, which may be shared between computers (ADR 0024). */
class JsonUpdatePreferencesRepository(
    installationDirectory: Path,
) : UpdatePreferencesRepository {
    private val store = JsonDocumentStore(installationDirectory)

    /** Preferences, not user data: an unreadable file is set aside and defaults apply, as for the settings. */
    override suspend fun load(): UpdatePreferences {
        val text = store.read(FILE_NAME) ?: return UpdatePreferences()
        return try {
            decodeDocument<UpdatePreferencesDocument>(text, UpdatePreferencesDocument.CURRENT_SCHEMA_VERSION).toDomain()
        } catch (e: UnsupportedSchemaException) {
            logger.info { "Update preferences are from a newer version, defaults apply: ${e.message}" }
            UpdatePreferences()
        } catch (e: IllegalArgumentException) {
            setAside(e)
        } catch (e: IllegalStateException) {
            setAside(e)
        }
    }

    private suspend fun setAside(e: Exception): UpdatePreferences {
        logger.warn(e) { "Update preferences could not be read, defaults apply" }
        store.quarantine(FILE_NAME)
        return UpdatePreferences()
    }

    override suspend fun save(preferences: UpdatePreferences) {
        store.write(FILE_NAME, json.encodeToString(UpdatePreferencesDocument.from(preferences)))
    }

    private companion object {
        const val FILE_NAME = "updates"
    }
}
