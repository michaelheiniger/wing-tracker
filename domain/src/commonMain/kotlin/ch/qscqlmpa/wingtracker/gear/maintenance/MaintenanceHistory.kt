package ch.qscqlmpa.wingtracker.gear.maintenance

/** The checks performed and planned for one piece of gear; shared by every kind of gear. */
data class MaintenanceHistory(
    val kind: GearKind,
    // Newest first, so history reads like a timeline.
    val checks: List<MaintenanceCheck> = emptyList(),
    val plannedChecks: List<PlannedCheck> = emptyList(),
) {
    init {
        require(checks.all { it.type in kind.allowedChecks }) { "A $kind does not accept this check type" }
        require(plannedChecks.all { it.type in kind.allowedChecks }) { "A $kind does not accept this check type" }
        require(checks.map { it.id }.toSet().size == checks.size) { "Check ids must be unique" }
        require(plannedChecks.map { it.id }.toSet().size == plannedChecks.size) { "Planned check ids must be unique" }
    }

    /** The check the interval clock counts from. */
    val lastPeriodicCheck: MaintenanceCheck? get() = checks.filter { it.restartsInterval }.maxByOrNull { it.date }

    val nextPlannedCheck: PlannedCheck? get() = plannedChecks.minByOrNull { it.date }

    fun withCheck(check: MaintenanceCheck): MaintenanceHistory {
        require(checks.none { it.id == check.id }) { "Check ${check.id.value} already exists" }
        return copy(checks = newestFirst(checks + check))
    }

    fun withUpdatedCheck(check: MaintenanceCheck): MaintenanceHistory {
        checkOf(check.id)
        return copy(checks = newestFirst(checks.map { if (it.id == check.id) check else it }))
    }

    fun withoutCheck(id: CheckId): MaintenanceHistory {
        checkOf(id)
        return copy(checks = checks.filter { it.id != id })
    }

    fun checkOf(id: CheckId): MaintenanceCheck =
        checks.firstOrNull { it.id == id } ?: throw NoSuchElementException("No check ${id.value}")

    fun withPlannedCheck(planned: PlannedCheck): MaintenanceHistory {
        require(plannedChecks.none { it.id == planned.id }) { "Planned check ${planned.id.value} already exists" }
        return copy(plannedChecks = plannedChecks + planned)
    }

    fun withUpdatedPlannedCheck(planned: PlannedCheck): MaintenanceHistory {
        plannedCheckOf(planned.id)
        return copy(plannedChecks = plannedChecks.map { if (it.id == planned.id) planned else it })
    }

    fun withoutPlannedCheck(id: PlannedCheckId): MaintenanceHistory {
        plannedCheckOf(id)
        return copy(plannedChecks = plannedChecks.filter { it.id != id })
    }

    fun plannedCheckOf(id: PlannedCheckId): PlannedCheck =
        plannedChecks.firstOrNull { it.id == id } ?: throw NoSuchElementException("No planned check ${id.value}")

    private fun newestFirst(list: List<MaintenanceCheck>) = list.sortedByDescending { it.date }
}
