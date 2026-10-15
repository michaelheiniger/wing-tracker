package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.gear.harness.Harness
import ch.qscqlmpa.wingtracker.gear.harness.HarnessId
import ch.qscqlmpa.wingtracker.gear.harness.HarnessRepository
import ch.qscqlmpa.wingtracker.gear.reserve.Reserve
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveId
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import java.nio.file.Path

/** One file per harness under the `harnesses` directory (ADR 0010). */
class JsonHarnessRepository(
    dataDirectory: Path,
) : HarnessRepository {
    private val store = JsonDocumentStore.forCollection(dataDirectory, "harnesses")

    override suspend fun findAll(): LoadResult<Harness> = store.loadAll(::decode)

    override suspend fun findById(id: HarnessId): Harness? = store.read(id.value)?.let(::decode)

    override suspend fun save(harness: Harness) {
        store.write(harness.id.value, json.encodeToString(HarnessDocument.from(harness)))
    }

    override suspend fun delete(id: HarnessId) = store.delete(id.value)

    private fun decode(text: String): Harness =
        decodeDocument<HarnessDocument>(text, HarnessDocument.CURRENT_SCHEMA_VERSION).toDomain()
}

/** One file per reserve parachute under the `reserves` directory (ADR 0010). */
class JsonReserveRepository(
    dataDirectory: Path,
) : ReserveRepository {
    private val store = JsonDocumentStore.forCollection(dataDirectory, "reserves")

    override suspend fun findAll(): LoadResult<Reserve> = store.loadAll(::decode)

    override suspend fun findById(id: ReserveId): Reserve? = store.read(id.value)?.let(::decode)

    override suspend fun save(reserve: Reserve) {
        store.write(reserve.id.value, json.encodeToString(ReserveDocument.from(reserve)))
    }

    override suspend fun delete(id: ReserveId) = store.delete(id.value)

    private fun decode(text: String): Reserve =
        decodeDocument<ReserveDocument>(text, ReserveDocument.CURRENT_SCHEMA_VERSION).toDomain()
}
