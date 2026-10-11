package ch.qscqlmpa.wingtracker.gear.reserve

import ch.qscqlmpa.wingtracker.shared.LoadResult

/** Port implemented by storage adapters (ADR 0010). */
interface ReserveRepository {
    suspend fun findAll(): LoadResult<Reserve>

    suspend fun findById(id: ReserveId): Reserve?

    suspend fun save(reserve: Reserve)

    suspend fun delete(id: ReserveId)
}
