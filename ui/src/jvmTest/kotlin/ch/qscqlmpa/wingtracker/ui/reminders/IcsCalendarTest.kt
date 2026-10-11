package ch.qscqlmpa.wingtracker.ui.reminders

import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private const val BACKSLASH = "\\"
private val GENERATED_AT = Instant.parse("2026-10-11T18:30:05Z")

private fun anEvent(
    uid: String = "planned-p-1@wing-tracker",
    date: LocalDate = LocalDate(2026, 12, 1),
    summary: String = "Sky Dancer: Full",
    description: String? = null,
) = IcsEvent(uid, date, summary, description)

private fun render(vararg events: IcsEvent) =
    renderIcs(events.toList(), alarmLeadDays = 30, generatedAt = GENERATED_AT, calendarName = "Wing Tracker")

class IcsCalendarTest {
    @Nested
    inner class Structure {
        @Test
        fun `wraps the events in a calendar with a version and product id`() {
            // given / when
            val text = render(anEvent())

            // then
            val lines = text.split("\r\n")
            assertThat(lines.first()).isEqualTo("BEGIN:VCALENDAR")
            assertThat(lines).contains("VERSION:2.0")
            assertThat(lines.filter { it.isNotEmpty() }.last()).isEqualTo("END:VCALENDAR")
        }

        @Test
        fun `ends every line with a carriage return and a line feed`() {
            // given / when
            val text = render(anEvent())

            // then
            assertThat(text).endsWith("\r\n")
            assertThat(text.replace("\r\n", "")).doesNotContain("\n")
        }

        @Test
        fun `renders an empty calendar when there are no events`() {
            // given / when
            val text = render()

            // then
            assertThat(text).doesNotContain("BEGIN:VEVENT")
            assertThat(text).contains("BEGIN:VCALENDAR").contains("END:VCALENDAR")
        }
    }

    @Nested
    inner class Events {
        @Test
        fun `renders an all-day event ending on the following day`() {
            // given / when
            val lines = render(anEvent(date = LocalDate(2026, 12, 31))).split("\r\n")

            // then
            assertThat(lines).contains("DTSTART;VALUE=DATE:20261231", "DTEND;VALUE=DATE:20270101")
        }

        @Test
        fun `keeps the stable id and stamps the moment of export in UTC`() {
            // given / when
            val lines = render(anEvent(uid = "due-wing-w-1@wing-tracker")).split("\r\n")

            // then
            assertThat(lines).contains("UID:due-wing-w-1@wing-tracker", "DTSTAMP:20261011T183005Z")
        }

        @Test
        fun `adds a display alarm the lead time ahead`() {
            // given / when
            val lines = render(anEvent(summary = "Sky Dancer: Full")).split("\r\n")

            // then
            assertThat(
                lines,
            ).contains("BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:Sky Dancer: Full", "TRIGGER:-P30D", "END:VALARM")
        }

        @Test
        fun `omits the description when there is none`() {
            // given / when
            val text = render(anEvent(description = null))

            // then
            assertThat(text.split("\r\n").filter { it.startsWith("DESCRIPTION:") }).hasSize(1)
        }

        @Test
        fun `renders several events in the order given`() {
            // given / when
            val text = render(anEvent(uid = "first@wing-tracker"), anEvent(uid = "second@wing-tracker"))

            // then
            assertThat(text.indexOf("first@wing-tracker")).isLessThan(text.indexOf("second@wing-tracker"))
        }
    }

    @Nested
    inner class Escaping {
        @Test
        fun `escapes commas, semicolons, backslashes and line breaks`() {
            // given / when
            val escaped = escapeText("a,b;c\\d\ne")

            // then
            assertThat(
                escaped,
            ).isEqualTo(listOf("a", "\\", ",b", "\\", ";c", "\\", "\\", "d", "\\", "ne").joinToString(""))
        }

        @Test
        fun `escapes a description with several lines`() {
            // given / when
            val lines = render(anEvent(description = "Alpine Wing Service\nLines within tolerance")).split("\r\n")

            // then
            assertThat(lines).contains("DESCRIPTION:Alpine Wing Service\\nLines within tolerance")
        }
    }

    @Nested
    inner class Folding {
        @Test
        fun `leaves a short line alone`() {
            assertThat(fold("SUMMARY:short")).isEqualTo("SUMMARY:short")
        }

        @Test
        fun `folds a long line so that no physical line exceeds 75 octets`() {
            // given
            val long = "SUMMARY:" + "x".repeat(200)

            // when
            val lines = fold(long).split("\r\n")

            // then
            assertThat(lines).hasSizeGreaterThan(1)
            assertThat(lines.map { it.encodeToByteArray().size }).allMatch { it <= 75 }
            assertThat(lines.drop(1)).allMatch { it.startsWith(" ") }
        }

        @Test
        fun `unfolding restores the original line`() {
            // given
            val long = "DESCRIPTION:" + "Lines within tolerance, ".repeat(10)

            // when
            val unfolded = fold(long).replace("\r\n ", "")

            // then
            assertThat(unfolded).isEqualTo(long)
        }

        @Test
        fun `never splits a multibyte character across lines`() {
            // given
            val long = "SUMMARY:" + "ä".repeat(100) + "→" + "ü".repeat(40)

            // when
            val folded = fold(long)

            // then
            assertThat(folded.split("\r\n").map { it.encodeToByteArray().size }).allMatch { it <= 75 }
            assertThat(folded.replace("\r\n ", "")).isEqualTo(long)
        }
    }
}
