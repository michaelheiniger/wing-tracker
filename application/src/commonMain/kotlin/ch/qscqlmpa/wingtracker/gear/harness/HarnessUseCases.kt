package ch.qscqlmpa.wingtracker.gear.harness

import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.wing.GearInUseException
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions
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
    private val transactions: LogbookTransactions,
    // Injectable so tests get deterministic ids.
    private val newId: () -> HarnessId = { HarnessId(Uuid.random().toString()) },
) {
    suspend operator fun invoke(details: HarnessDetails): Harness {
        val item = Harness(id = newId(), details = details)
        transactions.run(LogbookChange.GearRegistered(item.ref)) { repository.save(item) }
        return item
    }
}

class UpdateHarnessDetails(
    private val repository: HarnessRepository,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(
        id: HarnessId,
        details: HarnessDetails,
    ): Harness =
        transactions.run(LogbookChange.GearDetailsUpdated(id.ref)) {
            (
                repository.findById(
                    id,
                ) ?: throw HarnessNotFoundException(id)
            ).withDetails(details).also { repository.save(it) }
        }
}

class RetireHarness(
    private val repository: HarnessRepository,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(
        id: HarnessId,
        on: LocalDate,
        reason: String?,
    ): Harness =
        transactions.run(LogbookChange.GearRetired(id.ref)) {
            (
                repository.findById(
                    id,
                ) ?: throw HarnessNotFoundException(id)
            ).retire(on, reason).also { repository.save(it) }
        }
}

/** Deleting is for items entered by mistake; one with maintenance history is retired instead. */
class DeleteHarness(
    private val repository: HarnessRepository,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(id: HarnessId) =
        transactions.run(LogbookChange.GearDeleted(id.ref)) {
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

private val HarnessId.ref get() = GearRef(GearKind.Harness, value)
