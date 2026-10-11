package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGear
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceHistory
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import kotlinx.datetime.LocalDate

data class Wing(
    val id: WingId,
    val details: WingDetails,
    val status: GearStatus = GearStatus.Active,
    // Newest first, so history reads like a timeline.
    val checks: List<MaintenanceCheck> = emptyList(),
    val plannedChecks: List<PlannedCheck> = emptyList(),
) : MaintainedGear {
    init {
        if (status is GearStatus.Retired) {
            require(status.on >= details.inServiceDate) { "A wing cannot be retired before it entered service" }
        }
        // Builds the history once to validate the checks against what a wing accepts.
        history
    }

    override val isActive: Boolean get() = status == GearStatus.Active

    override val ref: GearRef get() = GearRef(GearKind.Wing, id.value)
    override val displayName: String get() = details.name
    override val inServiceDate: LocalDate get() = details.inServiceDate
    override val maintenanceInterval get() = details.maintenanceInterval
    override val history: MaintenanceHistory get() = MaintenanceHistory(GearKind.Wing, checks, plannedChecks)

    val nextPlannedCheck: PlannedCheck? get() = history.nextPlannedCheck

    override fun withHistory(history: MaintenanceHistory): Wing =
        copy(checks = history.checks, plannedChecks = history.plannedChecks)

    fun withDetails(details: WingDetails): Wing = copy(details = details)

    fun retire(
        on: LocalDate,
        reason: String? = null,
    ): Wing {
        check(isActive) { "Wing ${id.value} is already retired" }
        return copy(status = GearStatus.Retired(on, reason?.takeIf { it.isNotBlank() }))
    }
}
