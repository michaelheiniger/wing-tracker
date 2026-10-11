package ch.qscqlmpa.wingtracker.gear.harness

import ch.qscqlmpa.wingtracker.shared.LoadResult

/** Port implemented by storage adapters (ADR 0010). */
interface HarnessRepository {
    suspend fun findAll(): LoadResult<Harness>

    suspend fun findById(id: HarnessId): Harness?

    suspend fun save(harness: Harness)

    suspend fun delete(id: HarnessId)
}
