package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.sync.RecordKind
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

internal const val WINGS_FOLDER = "wings"
internal const val HARNESSES_FOLDER = "harnesses"
internal const val RESERVES_FOLDER = "reserves"
internal const val FLIGHTS_FOLDER = "flights"
internal const val ATTACHMENTS_FOLDER = "attachments"
internal const val LOGBOOK_SETTINGS_FILE = "settings"
internal const val DEVICE_SETTINGS_FILE = "device-settings"

/**
 * What synchronising needs to know about how this adapter lays out the data folder (ADR 0033), so nothing else
 * depends on it. Paths are relative to the data folder, with `/` between their parts.
 */
object JsonDataFolder {
    /** Files that stay on this device, in `.gitignore` syntax. */
    val deviceOnly: List<String> =
        listOf("/${JsonDocumentStore.QUARANTINE_FOLDER}/", "*.tmp", "/$DEVICE_SETTINGS_FILE.json")

    /** Whether the record once stored at [path] was moved to the quarantine, so it is missing but not deleted. */
    fun isQuarantined(
        dataDirectory: Path,
        path: String,
    ): Boolean {
        if (!path.endsWith(".json")) return false
        val target = dataDirectory.resolve(JsonDocumentStore.QUARANTINE_FOLDER).resolve(path)
        val folder = target.parent
        if (!Files.isDirectory(folder)) return false
        // A record quarantined again while an earlier copy is there gets a number (JsonDocumentStore.quarantine).
        val stem = target.name.removeSuffix(".json")
        val copy = Regex("""${Regex.escape(stem)}(-\d+)?\.json""")
        return folder.listDirectoryEntries().any { copy.matches(it.name) }
    }

    fun recordAt(path: String): SyncedRecord {
        val parts = path.split('/')
        val id = parts.last().removeSuffix(".json")
        val kind =
            when {
                parts.size == 1 && id == LOGBOOK_SETTINGS_FILE -> RecordKind.LogbookSettings
                parts.size != 2 -> RecordKind.Other
                else -> COLLECTIONS[parts.first()] ?: RecordKind.Other
            }
        return SyncedRecord(kind, id)
    }

    private val COLLECTIONS =
        mapOf(
            WINGS_FOLDER to RecordKind.Wing,
            HARNESSES_FOLDER to RecordKind.Harness,
            RESERVES_FOLDER to RecordKind.Reserve,
            FLIGHTS_FOLDER to RecordKind.Flight,
            ATTACHMENTS_FOLDER to RecordKind.Attachment,
        )
}
