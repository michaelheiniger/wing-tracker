package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGear
import ch.qscqlmpa.wingtracker.sync.FieldDifference
import ch.qscqlmpa.wingtracker.sync.RecordKind
import ch.qscqlmpa.wingtracker.sync.RecordSummary
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    /**
     * What names the record stored at [path] for the pilot, read the way its repository reads it. A version this
     * application cannot read, or a file that names nothing, has none.
     */
    fun summary(
        path: String,
        version: ByteArray,
    ): RecordSummary? {
        val text = version.decodeToString()
        return try {
            when (recordAt(path).kind) {
                RecordKind.Wing -> {
                    gear(decodeDocument<WingDocument>(text, WingDocument.CURRENT_SCHEMA_VERSION).toDomain())
                }

                RecordKind.Harness -> {
                    gear(decodeDocument<HarnessDocument>(text, HarnessDocument.CURRENT_SCHEMA_VERSION).toDomain())
                }

                RecordKind.Reserve -> {
                    gear(decodeDocument<ReserveDocument>(text, ReserveDocument.CURRENT_SCHEMA_VERSION).toDomain())
                }

                RecordKind.Flight -> {
                    val flight = decodeDocument<FlightDocument>(text, FlightDocument.CURRENT_SCHEMA_VERSION).toDomain()
                    RecordSummary.Flight(flight.details.date, flight.details.takeOffSite.shown)
                }

                else -> {
                    null
                }
            }
        } catch (_: UnsupportedSchemaException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    private fun gear(item: MaintainedGear) = RecordSummary.Gear(item.displayName)

    /**
     * The fields that differ between two versions of the file at [path], as stored, with nested fields written
     * like `checks[2].type`. A version is null where that side deleted the file. A file that is no document, such as
     * an attachment, or one that cannot be read, has no fields to compare.
     */
    fun differences(
        path: String,
        here: ByteArray?,
        there: ByteArray?,
    ): List<FieldDifference> {
        if (!path.endsWith(".json")) return emptyList()
        val hereFields = here?.let(::fieldsOf) ?: emptyMap()
        val thereFields = there?.let(::fieldsOf) ?: emptyMap()
        return (hereFields.keys + thereFields.keys)
            .filter { hereFields[it] != thereFields[it] }
            .map { FieldDifference(it, hereFields[it], thereFields[it]) }
    }

    // The format version is how a file is stored, not what it says.
    private fun fieldsOf(bytes: ByteArray): Map<String, String?> =
        try {
            buildMap { flatten("", json.parseToJsonElement(bytes.decodeToString())) }
                .filterKeys { it != "schemaVersion" }
        } catch (_: SerializationException) {
            emptyMap()
        }

    private fun MutableMap<String, String?>.flatten(
        prefix: String,
        element: JsonElement,
    ) {
        when (element) {
            is JsonObject -> {
                element.forEach { (key, value) ->
                    flatten(if (prefix.isEmpty()) key else "$prefix.$key", value)
                }
            }

            is JsonArray -> {
                element.forEachIndexed { index, value -> flatten("$prefix[$index]", value) }
            }

            JsonNull -> {
                put(prefix, null)
            }

            is JsonPrimitive -> {
                put(prefix, element.content)
            }
        }
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
