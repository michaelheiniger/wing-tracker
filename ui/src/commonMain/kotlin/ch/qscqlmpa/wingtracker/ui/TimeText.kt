package ch.qscqlmpa.wingtracker.ui

import kotlinx.datetime.LocalTime

private val HOUR_AND_MINUTE = Regex("""(\d{1,2}):([0-5]\d)""")
private const val HOURS_PER_DAY = 24
private const val TWO_DIGITS = 2

/** A time of day as `hh:mm`, whatever the machine's locale, like dates are (see [toDisplay]). */
fun LocalTime.toDisplay(): String =
    "${hour.toString().padStart(TWO_DIGITS, '0')}:${minute.toString().padStart(TWO_DIGITS, '0')}"

/** Reads `h:mm` or `hh:mm`; returns null for anything else, including hours past the end of the day. */
fun parseTimeOfDay(text: String): LocalTime? {
    val match = HOUR_AND_MINUTE.matchEntire(text.trim()) ?: return null
    val (h, m) = match.destructured
    return h.toInt().takeIf { it < HOURS_PER_DAY }?.let { LocalTime(it, m.toInt()) }
}
