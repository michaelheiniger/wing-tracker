package ch.qscqlmpa.wingtracker.gear.reserve

import ch.qscqlmpa.wingtracker.gear.wing.GearInUseException
import ch.qscqlmpa.wingtracker.shared.LoadResult
import kotlinx.datetime.LocalDate
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ReserveNotFoundException(
    id: ReserveId,
) : NoSuchElementException("No reserve with id ${id.value}")

class ReserveInUseException(
    id: ReserveId,
) : GearInUseException("Reserve ${id.value} has maintenance history and can only be retired")

@OptIn(ExperimentalUuidApi::class)
class RegisterReserve(
    private val repository: ReserveRepository,
    // Injectable so tests get deterministic ids.
    private val newId: () -> ReserveId = { ReserveId(Uuid.random().toString()) },
) {
    suspend operator fun invoke(details: ReserveDetails): Reserve {
        val item = Reserve(id = newId(), details = details)
        repository.save(item)
        return item
    }
}

class UpdateReserveDetails(
    private val repository: ReserveRepository,
) {
    suspend operator fun invoke(
        id: ReserveId,
        details: ReserveDetails,
    ): Reserve {
        val updated = (repository.findById(id) ?: throw ReserveNotFoundException(id)).withDetails(details)
        repository.save(updated)
        return updated
    }
}

class RetireReserve(
    private val repository: ReserveRepository,
) {
    suspend operator fun invoke(
        id: ReserveId,
        on: LocalDate,
        reason: String?,
    ): Reserve {
        val retired = (repository.findById(id) ?: throw ReserveNotFoundException(id)).retire(on, reason)
        repository.save(retired)
        return retired
    }
}

/** Deleting is for items entered by mistake; one with maintenance history is retired instead. */
class DeleteReserve(
    private val repository: ReserveRepository,
) {
    suspend operator fun invoke(id: ReserveId) {
        val item = repository.findById(id) ?: throw ReserveNotFoundException(id)
        if (item.checks.isNotEmpty()) throw ReserveInUseException(id)
        repository.delete(id)
    }
}

class ListReserves(
    private val repository: ReserveRepository,
) {
    /** Active items come first, then retired ones; each group sorted for a stable, scannable list. */
    suspend operator fun invoke(): LoadResult<Reserve> {
        val loaded = repository.findAll()
        val sorted =
            loaded.items.sortedWith(
                compareBy<Reserve> { !it.isActive }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.details.name },
            )
        return loaded.copy(items = sorted)
    }
}
