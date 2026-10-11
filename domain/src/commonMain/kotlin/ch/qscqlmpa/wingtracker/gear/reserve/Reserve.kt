package ch.qscqlmpa.wingtracker.gear.reserve

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGear
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceHistory
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.gear.requireIdentified
import ch.qscqlmpa.wingtracker.gear.requireMonthsOnly
import kotlinx.datetime.LocalDate

@JvmInline
value class ReserveId(
    val value: String,
) {
    init {
        require(PATTERN.matches(value)) { "Invalid reserve id '$value'" }
    }

    private companion object {
        val PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    }
}

data class ReserveDetails(
    val name: String,
    val manufacturer: String,
    val model: String,
    val size: String? = null,
    val serialNumber: String? = null,
    // Recorded for reference: manuals count a reserve's service life from it.
    val manufactureDate: LocalDate? = null,
    val acquisition: Acquisition,
    val inServiceDate: LocalDate,
    val maintenanceInterval: MaintenanceInterval? = null,
) {
    init {
        requireIdentified(name, manufacturer, model)
        requireMonthsOnly(maintenanceInterval)
    }
}

data class Reserve(
    val id: ReserveId,
    val details: ReserveDetails,
    val status: GearStatus = GearStatus.Active,
    val checks: List<MaintenanceCheck> = emptyList(),
    val plannedChecks: List<PlannedCheck> = emptyList(),
) : MaintainedGear {
    init {
        if (status is GearStatus.Retired) {
            require(status.on >= details.inServiceDate) { "A reserve cannot be retired before it entered service" }
        }
        history
    }

    val isActive: Boolean get() = status == GearStatus.Active

    override val ref: GearRef get() = GearRef(GearKind.Reserve, id.value)
    override val displayName: String get() = details.name
    override val inServiceDate: LocalDate get() = details.inServiceDate
    override val maintenanceInterval get() = details.maintenanceInterval
    override val history: MaintenanceHistory get() = MaintenanceHistory(GearKind.Reserve, checks, plannedChecks)

    override fun withHistory(history: MaintenanceHistory): Reserve =
        copy(checks = history.checks, plannedChecks = history.plannedChecks)

    fun withDetails(details: ReserveDetails): Reserve = copy(details = details)

    fun retire(
        on: LocalDate,
        reason: String? = null,
    ): Reserve {
        check(isActive) { "Reserve ${id.value} is already retired" }
        return copy(status = GearStatus.Retired(on, reason?.takeIf { it.isNotBlank() }))
    }
}
