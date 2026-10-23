package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.settings.DeviceSettings
import ch.qscqlmpa.wingtracker.settings.DeviceSettingsRepository
import ch.qscqlmpa.wingtracker.settings.LogbookSettings
import ch.qscqlmpa.wingtracker.settings.LogbookSettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

private const val LOGBOOK_FILE = "settings"
private const val DEVICE_FILE = "device-settings"

/** Settings every device synchronised with this logbook shares (ADR 0033). */
class JsonLogbookSettingsRepository(
    dataDirectory: Path,
) : LogbookSettingsRepository {
    private val settings = SettingsFile(dataDirectory, LOGBOOK_FILE, "Logbook settings")

    override suspend fun load(): LogbookSettings =
        settings.load(LogbookSettings()) {
            decodeDocument<LogbookSettingsDocument>(it, LogbookSettingsDocument.CURRENT_SCHEMA_VERSION).toDomain()
        }

    override suspend fun save(settings: LogbookSettings) =
        this.settings.save(json.encodeToString(LogbookSettingsDocument.from(settings)))
}

/** Settings of this device only, kept out of synchronisation (ADR 0033). */
class JsonDeviceSettingsRepository(
    dataDirectory: Path,
) : DeviceSettingsRepository {
    private val settings = SettingsFile(dataDirectory, DEVICE_FILE, "Device settings")

    override suspend fun load(): DeviceSettings =
        settings.load(DeviceSettings()) {
            decodeDocument<DeviceSettingsDocument>(it, DeviceSettingsDocument.CURRENT_SCHEMA_VERSION).toDomain()
        }

    override suspend fun save(settings: DeviceSettings) =
        this.settings.save(json.encodeToString(DeviceSettingsDocument.from(settings)))
}

/**
 * One settings file. Settings are preferences, not user data: an unreadable file is set aside and defaults apply,
 * because the application must always be able to start.
 */
private class SettingsFile(
    private val dataDirectory: Path,
    private val name: String,
    private val label: String,
) {
    private val store = JsonDocumentStore(dataDirectory)

    suspend fun <T> load(
        defaults: T,
        decode: (String) -> T,
    ): T {
        splitLegacySettings(dataDirectory)
        val text = store.read(name) ?: return defaults
        return try {
            decode(text)
        } catch (e: UnsupportedSchemaException) {
            logger.info { "$label are from a newer version, defaults apply: ${e.message}" }
            defaults
        } catch (e: IllegalArgumentException) {
            setAside(e, defaults)
        } catch (e: IllegalStateException) {
            setAside(e, defaults)
        }
    }

    private suspend fun <T> setAside(
        e: Exception,
        defaults: T,
    ): T {
        logger.warn(e) { "$label could not be read, defaults apply" }
        store.quarantine(name)
        return defaults
    }

    suspend fun save(text: String) {
        splitLegacySettings(dataDirectory)
        store.write(name, text)
    }
}

// One split at a time, so the two repositories loading at start never migrate the same file twice at once.
private val splitting = Mutex()

/**
 * Moves the device's settings out of a settings file written before the split (ADR 0033). The device file is written
 * first, so a crash in between only repeats the split. A file this version cannot read is left for the logbook
 * settings to set aside, as before the split.
 */
private suspend fun splitLegacySettings(dataDirectory: Path) =
    splitting.withLock {
        val store = JsonDocumentStore(dataDirectory)
        val text = store.read(LOGBOOK_FILE) ?: return@withLock
        val legacy =
            try {
                // A file without a version predates versioning.
                val version = json.parseToJsonElement(text).schemaVersion() ?: 0
                if (version > LegacySettingsDocument.LAST_SCHEMA_VERSION) return@withLock
                json.decodeFromString<LegacySettingsDocument>(text).also {
                    // Checked as a whole, so a value the domain rejects quarantines the original file.
                    it.toLogbook().toDomain()
                    it.toDevice().toDomain()
                }
            } catch (_: IllegalArgumentException) {
                return@withLock
            } catch (_: IllegalStateException) {
                return@withLock
            }
        if (store.read(DEVICE_FILE) == null) store.write(DEVICE_FILE, json.encodeToString(legacy.toDevice()))
        store.write(LOGBOOK_FILE, json.encodeToString(legacy.toLogbook()))
        logger.info { "Split the settings into logbook and device settings" }
    }
