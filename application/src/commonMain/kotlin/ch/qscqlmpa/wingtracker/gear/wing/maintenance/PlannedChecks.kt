package ch.qscqlmpa.wingtracker.gear.wing.maintenance

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PlanCheck(
    private val wings: WingRepository,
    private val clock: AppClock,
    private val newId: () -> PlannedCheckId = { PlannedCheckId(Uuid.random().toString()) },
) {
    suspend operator fun invoke(
        wingId: WingId,
        details: PlannedCheckDetails,
    ): Wing {
        if (details.date < clock.today()) throw PlannedDateInPastException()
        val planned =
            PlannedCheck(newId(), details.date, details.type, details.company.cleaned(), details.notes.cleaned())
        return wings.require(wingId).withPlannedCheck(planned).also { wings.save(it) }
    }
}

class UpdatePlannedCheck(
    private val wings: WingRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(
        wingId: WingId,
        id: PlannedCheckId,
        details: PlannedCheckDetails,
    ): Wing {
        if (details.date < clock.today()) throw PlannedDateInPastException()
        val planned = PlannedCheck(id, details.date, details.type, details.company.cleaned(), details.notes.cleaned())
        return wings.require(wingId).withUpdatedPlannedCheck(planned).also { wings.save(it) }
    }
}

class CancelPlannedCheck(
    private val wings: WingRepository,
) {
    suspend operator fun invoke(
        wingId: WingId,
        id: PlannedCheckId,
    ): Wing = wings.require(wingId).withoutPlannedCheck(id).also { wings.save(it) }
}
