package ch.qscqlmpa.wingtracker.ui

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val HOURS_AND_MINUTES = Regex("""(\d+)(?::([0-5]\d))?""")
private const val MINUTES_PER_HOUR = 60

/** Accepts `h` or `h:mm`, the way pilots write durations; returns null for anything else. */
fun parseHoursAndMinutes(text: String): Duration? {
    val match = HOURS_AND_MINUTES.matchEntire(text.trim()) ?: return null
    val (h, m) = match.destructured
    return h.toLong().hours + m.ifEmpty { "0" }.toLong().minutes
}

fun formatHoursAndMinutes(totalMinutes: Long): String {
    val minutes = (totalMinutes % MINUTES_PER_HOUR).toString().padStart(2, '0')
    return "${totalMinutes / MINUTES_PER_HOUR}:$minutes"
}
