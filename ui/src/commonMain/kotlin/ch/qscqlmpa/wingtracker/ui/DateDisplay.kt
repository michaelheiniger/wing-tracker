package ch.qscqlmpa.wingtracker.ui

import kotlinx.datetime.LocalDate

private const val YEAR_DIGITS = 4

/** Dates are shown day first, as the pilot expects, independent of the machine's locale. */
fun LocalDate.toDisplay(): String {
    val dayText = day.toString().padStart(2, '0')
    val monthText = (month.ordinal + 1).toString().padStart(2, '0')
    return "$dayText-$monthText-${year.toString().padStart(YEAR_DIGITS, '0')}"
}
