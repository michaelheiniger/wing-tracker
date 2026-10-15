package ch.qscqlmpa.wingtracker.ui

import kotlinx.datetime.LocalDate

private const val YEAR_DIGITS = 4

// The same separator twice; dots and slashes are accepted because that is how many pilots type dates.
private val DAY_MONTH_YEAR = Regex("""(?<day>\d{1,2})(?<sep>[-./])(?<month>\d{1,2})\k<sep>(?<year>\d{4})""")

/** Dates are shown day first, as the pilot expects, independent of the machine's locale. */
fun LocalDate.toDisplay(): String {
    val dayText = day.toString().padStart(2, '0')
    val monthText = (month.ordinal + 1).toString().padStart(2, '0')
    return "$dayText-$monthText-${year.toString().padStart(YEAR_DIGITS, '0')}"
}

/** Reads a typed date, day first like [toDisplay]; returns null when it is not a real calendar day. */
fun parseDate(text: String): LocalDate? {
    val match = DAY_MONTH_YEAR.matchEntire(text.trim()) ?: return null

    fun part(name: String) = checkNotNull(match.groups[name]).value.toInt()
    return try {
        LocalDate(part("year"), part("month"), part("day"))
    } catch (_: IllegalArgumentException) {
        null
    }
}
