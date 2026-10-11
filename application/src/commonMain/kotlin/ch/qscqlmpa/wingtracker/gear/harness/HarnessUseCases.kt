package ch.qscqlmpa.wingtracker.gear.harness

import ch.qscqlmpa.wingtracker.gear.wing.GearInUseException
import ch.qscqlmpa.wingtracker.shared.LoadResult
import kotlinx.datetime.LocalDate
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class HarnessNotFoundException(
    id: HarnessId,
) : NoSuchElementException("No harness with id ${id.value}")

class HarnessInUseException(
    id: HarnessId,
) : GearInUseException("Harness ${id.value} has maintenance history and can only be retired")

@OptIn(ExperimentalUuidApi::class)
class RegisterHarness(
    private val repository: HarnessRepository,
    // Injectable so tests get deterministic ids.
    private val newId: () -> HarnessId = { HarnessId(Uuid.random().toString()) },
) {
    suspend operator fun invoke(details: HarnessDetails): Harness {
        val item = Harness(id = newId(), details = details)
        repository.save(item)
        return item
    }
}

class UpdateHarnessDetails(
    private val repository: HarnessRepository,
) {
    suspend operator fun invoke(
        id: HarnessId,
        details: HarnessDetails,
    ): Harness {
        val updated = (repository.findById(id) ?: throw HarnessNotFoundException(id)).withDetails(details)
        repository.save(updated)
        return updated
    }
}

class RetireHarness(
    private val repository: HarnessRepository,
) {
    suspend operator fun invoke(
        id: HarnessId,
        on: LocalDate,
        reason: String?,
    ): Harness {
        val retired = (repository.findById(id) ?: throw HarnessNotFoundException(id)).retire(on, reason)
        repository.save(retired)
        return retired
    }
}

/** Deleting is for items entered by mistake; one with maintenance history is retired instead. */
class DeleteHarness(
    private val repository: HarnessRepository,
) {
    suspend operator fun invoke(id: HarnessId) {
        val item = repository.findById(id) ?: throw HarnessNotFoundException(id)
        if (item.checks.isNotEmpty()) throw HarnessInUseException(id)
        repository.delete(id)
    }
}

class ListHarnesses(
    private val repository: HarnessRepository,
) {
    /** Active items come first, then retired ones; each group sorted for a stable, scannable list. */
    suspend operator fun invoke(): LoadResult<Harness> {
        val loaded = repository.findAll()
        val sorted =
            loaded.items.sortedWith(
                compareBy<Harness> { !it.isActive }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.details.name },
            )
        return loaded.copy(items = sorted)
    }
}
