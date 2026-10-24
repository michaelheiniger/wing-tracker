package ch.qscqlmpa.wingtracker.gear.reserve

import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.wing.GearInUseException
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions
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
    private val transactions: LogbookTransactions,
    // Injectable so tests get deterministic ids.
    private val newId: () -> ReserveId = { ReserveId(Uuid.random().toString()) },
) {
    suspend operator fun invoke(details: ReserveDetails): Reserve {
        val item = Reserve(id = newId(), details = details)
        transactions.run(LogbookChange.GearRegistered(item.ref)) { repository.save(item) }
        return item
    }
}

class UpdateReserveDetails(
    private val repository: ReserveRepository,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(
        id: ReserveId,
        details: ReserveDetails,
    ): Reserve =
        transactions.run(LogbookChange.GearDetailsUpdated(id.ref)) {
            (
                repository.findById(
                    id,
                ) ?: throw ReserveNotFoundException(id)
            ).withDetails(details).also { repository.save(it) }
        }
}

class RetireReserve(
    private val repository: ReserveRepository,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(
        id: ReserveId,
        on: LocalDate,
        reason: String?,
    ): Reserve =
        transactions.run(LogbookChange.GearRetired(id.ref)) {
            (
                repository.findById(
                    id,
                ) ?: throw ReserveNotFoundException(id)
            ).retire(on, reason).also { repository.save(it) }
        }
}

/** Deleting is for items entered by mistake; one with maintenance history is retired instead. */
class DeleteReserve(
    private val repository: ReserveRepository,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(id: ReserveId) =
        transactions.run(LogbookChange.GearDeleted(id.ref)) {
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

private val ReserveId.ref get() = GearRef(GearKind.Reserve, value)
