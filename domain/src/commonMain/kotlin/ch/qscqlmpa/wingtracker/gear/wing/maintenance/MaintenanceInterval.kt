package ch.qscqlmpa.wingtracker.gear.wing.maintenance

import kotlin.time.Duration

/**
 * "Every [months] months and/or every [hours] flight hours, whichever comes first", the way
 * manufacturers state it. The limits differ per model, so they are data, never constants.
 */
data class MaintenanceInterval(
    val months: Int? = null,
    val hours: Duration? = null,
) {
    init {
        require(months != null || hours != null) { "An interval needs a number of months, hours, or both" }
        require(months == null || months > 0) { "The interval in months must be positive" }
        require(hours == null || hours.isPositive()) { "The interval in hours must be positive" }
    }
}
