package ch.qscqlmpa.wingtracker.gear.maintenance

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Duration

/** Hours flown on one day; maintenance only needs this much of a flight, which keeps gear independent of flights. */
data class FlownHours(
    val date: LocalDate,
    val duration: Duration,
)

/**
 * Where a wing stands against its interval. "Due" means the interval is reached or passed, by date or hours;
 * a softer "due soon" belongs to reminders and is not decided here.
 */
data class MaintenanceStatus(
    // The clock starts at the last periodic check, or at the in-service date when there is none.
    val since: LocalDate,
    val dueDate: LocalDate?,
    val hoursSince: Duration,
    val hoursLimit: Duration?,
    val isDue: Boolean,
) {
    val hoursRemaining: Duration? get() = hoursLimit?.let { (it - hoursSince).coerceAtLeast(Duration.ZERO) }
}

/** Returns null for a wing without an interval rule, because there is nothing to be due against. */
fun MaintainedGear.maintenanceStatus(
    flown: List<FlownHours>,
    today: LocalDate,
): MaintenanceStatus? {
    val interval = maintenanceInterval ?: return null
    val since = history.lastPeriodicCheck?.date ?: inServiceDate
    // Hours count from the start of the clock day: a flight on the day of a check happened after the check.
    val hoursSince = flown.filter { it.date >= since }.fold(Duration.ZERO) { sum, f -> sum + f.duration }
    val dueDate = interval.months?.let { since.plus(it, DateTimeUnit.MONTH) }
    val dueByDate = dueDate != null && today >= dueDate
    val dueByHours = interval.hours != null && hoursSince >= interval.hours
    return MaintenanceStatus(since, dueDate, hoursSince, interval.hours, dueByDate || dueByHours)
}
