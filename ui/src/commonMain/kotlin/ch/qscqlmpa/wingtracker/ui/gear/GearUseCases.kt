package ch.qscqlmpa.wingtracker.ui.gear

import ch.qscqlmpa.wingtracker.gear.maintenance.GetMaintenanceStatuses
import ch.qscqlmpa.wingtracker.gear.wing.DeleteWing
import ch.qscqlmpa.wingtracker.gear.wing.ListWings
import ch.qscqlmpa.wingtracker.gear.wing.RegisterWing
import ch.qscqlmpa.wingtracker.gear.wing.RetireWing
import ch.qscqlmpa.wingtracker.gear.wing.UpdateWingDetails
import ch.qscqlmpa.wingtracker.ui.gear.simple.SimpleGearOps

/** Use cases the gear section needs; passed in so this module never sees the DI framework. */
class GearUseCases(
    val listWings: ListWings,
    val registerWing: RegisterWing,
    val updateWingDetails: UpdateWingDetails,
    val retireWing: RetireWing,
    val deleteWing: DeleteWing,
    val getMaintenanceStatuses: GetMaintenanceStatuses,
    val harnesses: SimpleGearOps,
    val reserves: SimpleGearOps,
)
