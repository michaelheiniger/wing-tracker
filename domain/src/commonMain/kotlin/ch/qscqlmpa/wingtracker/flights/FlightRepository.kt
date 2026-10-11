package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.shared.LoadResult

/** Port implemented by storage adapters (ADR 0010). */
interface FlightRepository {
    suspend fun findAll(): LoadResult<Flight>

    suspend fun findById(id: FlightId): Flight?

    suspend fun save(flight: Flight)

    suspend fun delete(id: FlightId)
}
