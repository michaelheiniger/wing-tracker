package ch.qscqlmpa.wingtracker.ui.gear.wing.maintenance

import ch.qscqlmpa.wingtracker.gear.wing.maintenance.CancelPlannedCheck
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.DeleteCheck
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.GetWingMaintenance
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.PlanCheck
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.ReadAttachment
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.RecordCheck
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.UpdateCheck
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.UpdatePlannedCheck

/** Use cases the maintenance screen needs; passed in so this module never sees the DI framework. */
class MaintenanceUseCases(
    val getWingMaintenance: GetWingMaintenance,
    val recordCheck: RecordCheck,
    val updateCheck: UpdateCheck,
    val deleteCheck: DeleteCheck,
    val planCheck: PlanCheck,
    val updatePlannedCheck: UpdatePlannedCheck,
    val cancelPlannedCheck: CancelPlannedCheck,
    val readAttachment: ReadAttachment,
)
