package ch.qscqlmpa.wingtracker.gear.maintenance

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PlanCheck(
    private val gear: MaintainedGearRepository,
    private val clock: AppClock,
    private val transactions: LogbookTransactions,
    private val newId: () -> PlannedCheckId = { PlannedCheckId(Uuid.random().toString()) },
) {
    suspend operator fun invoke(
        ref: GearRef,
        details: PlannedCheckDetails,
    ): MaintainedGear {
        if (details.date < clock.today()) throw PlannedDateInPastException()
        val planned =
            PlannedCheck(newId(), details.date, details.type, details.company.cleaned(), details.notes.cleaned())
        return transactions.run(LogbookChange.CheckPlanned(ref, planned.id)) {
            val item = gear.require(ref)
            item.withHistory(item.history.withPlannedCheck(planned)).also { gear.save(it) }
        }
    }
}

class UpdatePlannedCheck(
    private val gear: MaintainedGearRepository,
    private val clock: AppClock,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(
        ref: GearRef,
        id: PlannedCheckId,
        details: PlannedCheckDetails,
    ): MaintainedGear {
        if (details.date < clock.today()) throw PlannedDateInPastException()
        val planned = PlannedCheck(id, details.date, details.type, details.company.cleaned(), details.notes.cleaned())
        return transactions.run(LogbookChange.PlannedCheckUpdated(ref, id)) {
            val item = gear.require(ref)
            item.withHistory(item.history.withUpdatedPlannedCheck(planned)).also { gear.save(it) }
        }
    }
}

class CancelPlannedCheck(
    private val gear: MaintainedGearRepository,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(
        ref: GearRef,
        id: PlannedCheckId,
    ): MaintainedGear =
        transactions.run(LogbookChange.PlannedCheckCancelled(ref, id)) {
            val item = gear.require(ref)
            item.withHistory(item.history.withoutPlannedCheck(id)).also { gear.save(it) }
        }
}
