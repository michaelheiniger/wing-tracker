package ch.qscqlmpa.wingtracker.ui.gear.maintenance

import ch.qscqlmpa.wingtracker.gear.maintenance.CancelPlannedCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.DeleteCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.GetGearMaintenance
import ch.qscqlmpa.wingtracker.gear.maintenance.PlanCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.ReadAttachment
import ch.qscqlmpa.wingtracker.gear.maintenance.RecordCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.UpdateCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.UpdatePlannedCheck

/** Use cases the maintenance screen needs; passed in so this module never sees the DI framework. */
class MaintenanceUseCases(
    val getGearMaintenance: GetGearMaintenance,
    val recordCheck: RecordCheck,
    val updateCheck: UpdateCheck,
    val deleteCheck: DeleteCheck,
    val planCheck: PlanCheck,
    val updatePlannedCheck: UpdatePlannedCheck,
    val cancelPlannedCheck: CancelPlannedCheck,
    val readAttachment: ReadAttachment,
)
