package ch.qscqlmpa.wingtracker.storage.json

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

/**
 * Decodes a stored document, checking its schema version before its shape: a file from a newer
 * version may hold fields or values this version does not know, and must be left in place as newer
 * data instead of being quarantined as corrupt (ADR 0015).
 */
internal inline fun <reified T> decodeDocument(
    text: String,
    currentSchemaVersion: Int,
): T {
    val element = json.parseToJsonElement(text)
    val schemaVersion = ((element as? JsonObject)?.get("schemaVersion") as? JsonPrimitive)?.intOrNull
    if (schemaVersion != null && schemaVersion > currentSchemaVersion) {
        throw UnsupportedSchemaException(
            "Schema version $schemaVersion was written by a newer version of the application, " +
                "this one reads up to $currentSchemaVersion",
        )
    }
    return json.decodeFromJsonElement<T>(element)
}
