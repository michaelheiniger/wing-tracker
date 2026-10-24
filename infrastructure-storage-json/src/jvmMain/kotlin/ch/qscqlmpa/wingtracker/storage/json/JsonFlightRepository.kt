package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import java.nio.file.Path

/** One file per flight under the `flights` directory (ADR 0010). */
class JsonFlightRepository(
    dataDirectory: Path,
) : FlightRepository {
    private val store = JsonDocumentStore.forCollection(dataDirectory, FLIGHTS_FOLDER)

    override suspend fun findAll(): LoadResult<Flight> = store.loadAll(::decode)

    override suspend fun findById(id: FlightId): Flight? = store.read(id.value)?.let(::decode)

    override suspend fun save(flight: Flight) {
        store.write(flight.id.value, json.encodeToString(FlightDocument.from(flight)))
    }

    override suspend fun delete(id: FlightId) = store.delete(id.value)

    private fun decode(text: String): Flight =
        decodeDocument<FlightDocument>(text, FlightDocument.CURRENT_SCHEMA_VERSION).toDomain()
}
