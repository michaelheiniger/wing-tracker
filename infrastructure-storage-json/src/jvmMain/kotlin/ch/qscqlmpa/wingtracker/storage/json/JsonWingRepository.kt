package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import kotlinx.serialization.json.Json
import java.nio.file.Path

/** One file per wing under the `wings` directory (ADR 0010). */
class JsonWingRepository(
    dataDirectory: Path,
) : WingRepository {
    private val store = JsonDocumentStore.forCollection(dataDirectory, "wings")

    override suspend fun findAll(): LoadResult<Wing> = store.loadAll(::decode)

    override suspend fun findById(id: WingId): Wing? = store.read(id.value)?.let(::decode)

    override suspend fun save(wing: Wing) {
        store.write(wing.id.value, json.encodeToString(WingDocument.from(wing)))
    }

    override suspend fun delete(id: WingId) = store.delete(id.value)

    private fun decode(text: String): Wing =
        decodeDocument<WingDocument>(text, WingDocument.CURRENT_SCHEMA_VERSION).toDomain()
}

// encodeDefaults is required: schemaVersion equals its default and must still be written to disk.
internal val json =
    Json {
        prettyPrint = true
        encodeDefaults = true
    }
