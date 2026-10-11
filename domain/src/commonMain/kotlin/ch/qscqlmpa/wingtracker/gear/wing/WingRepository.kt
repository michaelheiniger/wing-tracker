package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.shared.LoadResult

/** Port implemented by storage adapters, so JSON, cloud or a database can be swapped in (ADR 0010). */
interface WingRepository {
    suspend fun findAll(): LoadResult<Wing>

    suspend fun findById(id: WingId): Wing?

    suspend fun save(wing: Wing)

    suspend fun delete(id: WingId)
}
