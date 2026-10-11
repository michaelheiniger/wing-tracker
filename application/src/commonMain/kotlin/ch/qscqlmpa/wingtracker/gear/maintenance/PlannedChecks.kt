package ch.qscqlmpa.wingtracker.gear.maintenance

import ch.qscqlmpa.wingtracker.AppClock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PlanCheck(
    private val gear: MaintainedGearRepository,
    private val clock: AppClock,
    private val newId: () -> PlannedCheckId = { PlannedCheckId(Uuid.random().toString()) },
) {
    suspend operator fun invoke(
        ref: GearRef,
        details: PlannedCheckDetails,
    ): MaintainedGear {
        if (details.date < clock.today()) throw PlannedDateInPastException()
        val planned =
            PlannedCheck(newId(), details.date, details.type, details.company.cleaned(), details.notes.cleaned())
        val item = gear.require(ref)
        return item.withHistory(item.history.withPlannedCheck(planned)).also { gear.save(it) }
    }
}

class UpdatePlannedCheck(
    private val gear: MaintainedGearRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(
        ref: GearRef,
        id: PlannedCheckId,
        details: PlannedCheckDetails,
    ): MaintainedGear {
        if (details.date < clock.today()) throw PlannedDateInPastException()
        val planned = PlannedCheck(id, details.date, details.type, details.company.cleaned(), details.notes.cleaned())
        val item = gear.require(ref)
        return item.withHistory(item.history.withUpdatedPlannedCheck(planned)).also { gear.save(it) }
    }
}

class CancelPlannedCheck(
    private val gear: MaintainedGearRepository,
) {
    suspend operator fun invoke(
        ref: GearRef,
        id: PlannedCheckId,
    ): MaintainedGear {
        val item = gear.require(ref)
        return item.withHistory(item.history.withoutPlannedCheck(id)).also { gear.save(it) }
    }
}
