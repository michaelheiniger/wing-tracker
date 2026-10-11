package ch.qscqlmpa.wingtracker.gear.maintenance

import kotlinx.datetime.LocalDate

/** What the maintenance use cases need from a piece of gear, whatever its kind. */
interface MaintainedGear {
    val ref: GearRef
    val displayName: String
    val inServiceDate: LocalDate
    val maintenanceInterval: MaintenanceInterval?
    val history: MaintenanceHistory

    fun withHistory(history: MaintenanceHistory): MaintainedGear
}
