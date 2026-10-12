package ch.qscqlmpa.wingtracker.gear.maintenance

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.time.Duration

/** Hours flown on one day; maintenance only needs this much of a flight, which keeps gear independent of flights. */
data class FlownHours(
    val date: LocalDate,
    val duration: Duration,
)

enum class DueState { Ok, DueSoon, Due }

/**
 * Where a piece of gear stands against its interval. "Due" means the interval is reached or passed, by date or hours;
 * "due soon" means it will be within the lead times.
 */
data class MaintenanceStatus(
    // See [maintenanceClockStart].
    val since: LocalDate,
    val dueDate: LocalDate?,
    val hoursSince: Duration,
    val hoursLimit: Duration?,
    val state: DueState,
) {
    val isDue: Boolean get() = state == DueState.Due
    val isDueSoon: Boolean get() = state == DueState.DueSoon

    val hoursRemaining: Duration? get() = hoursLimit?.let { (it - hoursSince).coerceAtLeast(Duration.ZERO) }
}

/**
 * The clock starts at the last periodic check; without one, at the in-service date, else at the purchase date.
 * Null when none of them is known.
 */
val MaintainedGear.maintenanceClockStart: LocalDate? get() =
    history.lastPeriodicCheck?.date ?: inServiceDate
        ?: purchaseDate

/**
 * Returns null for gear without an interval rule, or whose clock has no start yet (no check, no in-service date
 * and no purchase date): there is nothing to be due against.
 */
fun MaintainedGear.maintenanceStatus(
    flown: List<FlownHours>,
    today: LocalDate,
    leadTimes: ReminderLeadTimes = ReminderLeadTimes.DEFAULT,
): MaintenanceStatus? {
    val interval = maintenanceInterval ?: return null
    val since = maintenanceClockStart ?: return null
    // Hours count from the start of the clock day: a flight on the day of a check happened after the check.
    val hoursSince = flown.filter { it.date >= since }.fold(Duration.ZERO) { sum, f -> sum + f.duration }
    val dueDate = interval.months?.let { since.plus(it, DateTimeUnit.MONTH) }
    val dueByDate = dueDate != null && today >= dueDate
    val dueByHours = interval.hours != null && hoursSince >= interval.hours
    val soonByDate = dueDate != null && today.daysUntil(dueDate) <= leadTimes.days
    val soonByHours = interval.hours != null && interval.hours - hoursSince <= leadTimes.hours
    val state =
        when {
            dueByDate || dueByHours -> DueState.Due
            soonByDate || soonByHours -> DueState.DueSoon
            else -> DueState.Ok
        }
    return MaintenanceStatus(since, dueDate, hoursSince, interval.hours, state)
}
