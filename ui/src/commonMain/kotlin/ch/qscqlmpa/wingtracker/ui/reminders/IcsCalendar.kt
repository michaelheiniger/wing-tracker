package ch.qscqlmpa.wingtracker.ui.reminders

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val LINE_LIMIT_OCTETS = 75
private const val CRLF = "\r\n"
private const val YEAR_DIGITS = 4
private const val TWO_DIGITS = 2

/**
 * Renders an iCalendar file (RFC 5545) with one all-day event per entry, each with a display alarm
 * [alarmLeadDays] before it. Lines end with CRLF and are folded at 75 octets, as calendar apps require.
 */
fun renderIcs(
    events: List<IcsEvent>,
    alarmLeadDays: Int,
    generatedAt: Instant,
    calendarName: String,
): String {
    val stamp = generatedAt.toLocalDateTime(TimeZone.UTC).let { "${it.date.compact()}T${it.time.compact()}Z" }
    val lines =
        mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//wing-tracker//Gear maintenance//EN",
            "CALSCALE:GREGORIAN",
        )
    lines += "X-WR-CALNAME:${escapeText(calendarName)}"
    events.forEach { event ->
        lines += "BEGIN:VEVENT"
        lines += "UID:${event.uid}"
        lines += "DTSTAMP:$stamp"
        lines += "DTSTART;VALUE=DATE:${event.date.compact()}"
        // The end of an all-day event is exclusive, so it names the following day.
        lines += "DTEND;VALUE=DATE:${event.date.plus(1, DateTimeUnit.DAY).compact()}"
        lines += "SUMMARY:${escapeText(event.summary)}"
        event.description?.let { lines += "DESCRIPTION:${escapeText(it)}" }
        lines += "BEGIN:VALARM"
        lines += "ACTION:DISPLAY"
        lines += "DESCRIPTION:${escapeText(event.summary)}"
        lines += "TRIGGER:-P${alarmLeadDays}D"
        lines += "END:VALARM"
        lines += "END:VEVENT"
    }
    lines += "END:VCALENDAR"
    return lines.joinToString(separator = "") { fold(it) + CRLF }
}

private fun LocalDate.compact() = "${year.pad(YEAR_DIGITS)}${(month.ordinal + 1).pad(TWO_DIGITS)}${day.pad(TWO_DIGITS)}"

private fun kotlinx.datetime.LocalTime.compact() =
    "${hour.pad(TWO_DIGITS)}${minute.pad(TWO_DIGITS)}${second.pad(TWO_DIGITS)}"

private fun Int.pad(width: Int) = toString().padStart(width, '0')

/** Commas, semicolons, backslashes and line breaks have a meaning in iCalendar text and must be escaped. */
internal fun escapeText(text: String): String =
    buildString {
        text.replace("\r\n", "\n").forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                ';' -> append("\\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                else -> append(char)
            }
        }
    }

/** Folds a long line by breaking it and starting each continuation with a space, never inside a character. */
internal fun fold(line: String): String {
    if (line.encodeToByteArray().size <= LINE_LIMIT_OCTETS) return line
    val result = StringBuilder()
    var octets = 0
    var limit = LINE_LIMIT_OCTETS
    var index = 0
    while (index < line.length) {
        val codePointLength = if (line[index].isHighSurrogate() && index + 1 < line.length) 2 else 1
        val piece = line.substring(index, index + codePointLength)
        val size = piece.encodeToByteArray().size
        if (octets + size > limit) {
            result.append(CRLF).append(' ')
            octets = 0
            // A continuation line starts with one space, which counts towards its limit.
            limit = LINE_LIMIT_OCTETS - 1
        }
        result.append(piece)
        octets += size
        index += codePointLength
    }
    return result.toString()
}
