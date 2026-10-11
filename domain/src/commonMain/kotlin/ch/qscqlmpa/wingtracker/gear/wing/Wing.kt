package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.CheckId
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.PlannedCheckId
import kotlinx.datetime.LocalDate

data class Wing(
    val id: WingId,
    val details: WingDetails,
    val status: GearStatus = GearStatus.Active,
    // Newest first, so history reads like a timeline.
    val checks: List<MaintenanceCheck> = emptyList(),
    val plannedChecks: List<PlannedCheck> = emptyList(),
) {
    init {
        if (status is GearStatus.Retired) {
            require(status.on >= details.inServiceDate) { "A wing cannot be retired before it entered service" }
        }
        require(checks.map { it.id }.toSet().size == checks.size) { "Check ids must be unique" }
        require(plannedChecks.map { it.id }.toSet().size == plannedChecks.size) { "Planned check ids must be unique" }
    }

    val isActive: Boolean get() = status == GearStatus.Active

    /** The check the interval clock counts from. */
    val lastPeriodicCheck: MaintenanceCheck? get() = checks.filter { it.restartsInterval }.maxByOrNull { it.date }

    val nextPlannedCheck: PlannedCheck? get() = plannedChecks.minByOrNull { it.date }

    fun withDetails(details: WingDetails): Wing = copy(details = details)

    fun retire(
        on: LocalDate,
        reason: String? = null,
    ): Wing {
        check(isActive) { "Wing ${id.value} is already retired" }
        return copy(status = GearStatus.Retired(on, reason?.takeIf { it.isNotBlank() }))
    }

    fun withCheck(check: MaintenanceCheck): Wing {
        require(checks.none { it.id == check.id }) { "Check ${check.id.value} already exists" }
        return copy(checks = sortedNewestFirst(checks + check))
    }

    fun withUpdatedCheck(check: MaintenanceCheck): Wing {
        checkOf(check.id)
        return copy(checks = sortedNewestFirst(checks.map { if (it.id == check.id) check else it }))
    }

    fun withoutCheck(id: CheckId): Wing {
        checkOf(id)
        return copy(checks = checks.filter { it.id != id })
    }

    fun checkOf(id: CheckId): MaintenanceCheck =
        checks.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("No check ${id.value} on wing ${this.id.value}")

    fun withPlannedCheck(planned: PlannedCheck): Wing {
        require(plannedChecks.none { it.id == planned.id }) { "Planned check ${planned.id.value} already exists" }
        return copy(plannedChecks = plannedChecks + planned)
    }

    fun withUpdatedPlannedCheck(planned: PlannedCheck): Wing {
        plannedCheckOf(planned.id)
        return copy(plannedChecks = plannedChecks.map { if (it.id == planned.id) planned else it })
    }

    fun withoutPlannedCheck(id: PlannedCheckId): Wing {
        plannedCheckOf(id)
        return copy(plannedChecks = plannedChecks.filter { it.id != id })
    }

    fun plannedCheckOf(id: PlannedCheckId): PlannedCheck =
        plannedChecks.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("No planned check ${id.value} on wing ${this.id.value}")

    private fun sortedNewestFirst(list: List<MaintenanceCheck>) = list.sortedByDescending { it.date }
}
