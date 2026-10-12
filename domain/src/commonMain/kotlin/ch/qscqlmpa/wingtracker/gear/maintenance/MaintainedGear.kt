package ch.qscqlmpa.wingtracker.gear.maintenance

import kotlinx.datetime.LocalDate

/** What the maintenance use cases need from a piece of gear, whatever its kind. */
interface MaintainedGear {
    val ref: GearRef
    val displayName: String
    val inServiceDate: LocalDate?

    // Where the maintenance clock starts when there is no check yet and no in-service date.
    val purchaseDate: LocalDate?

    // Retired gear leaves reminders, but keeps its history.
    val isActive: Boolean
    val maintenanceInterval: MaintenanceInterval?
    val history: MaintenanceHistory

    fun withHistory(history: MaintenanceHistory): MaintainedGear
}
