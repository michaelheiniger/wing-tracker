package ch.qscqlmpa.wingtracker.gear.maintenance

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * How early a check counts as "due soon": by days for date-based limits and by flight hours for a wing's hour limit.
 * The pilot tunes both in the settings; [DEFAULT] only applies until then.
 */
data class ReminderLeadTimes(
    val days: Int,
    val hours: Duration,
) {
    init {
        require(days > 0) { "The lead time in days must be positive" }
        require(hours.isPositive()) { "The lead time in hours must be positive" }
    }

    companion object {
        val DEFAULT = ReminderLeadTimes(days = DEFAULT_DAYS, hours = DEFAULT_HOURS.hours)
    }
}

private const val DEFAULT_DAYS = 30
private const val DEFAULT_HOURS = 10
