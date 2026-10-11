package ch.qscqlmpa.wingtracker.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class DurationTextTest {
    @Nested
    inner class Parse {
        @Test
        fun `reads hours and minutes`() {
            assertThat(parseHoursAndMinutes("1:35")).isEqualTo(1.hours + 35.minutes)
        }

        @Test
        fun `reads whole hours`() {
            assertThat(parseHoursAndMinutes("12")).isEqualTo(12.hours)
        }

        @Test
        fun `ignores surrounding spaces`() {
            assertThat(parseHoursAndMinutes(" 0:45 ")).isEqualTo(45.minutes)
        }

        @Test
        fun `rejects minutes of sixty or more`() {
            assertThat(parseHoursAndMinutes("1:60")).isNull()
        }

        @Test
        fun `rejects text that is not a duration`() {
            assertThat(parseHoursAndMinutes("one hour")).isNull()
        }
    }

    @Nested
    inner class Format {
        @Test
        fun `pads the minutes`() {
            assertThat(formatHoursAndMinutes(65)).isEqualTo("1:05")
        }

        @Test
        fun `round-trips through parse`() {
            assertThat(parseHoursAndMinutes(formatHoursAndMinutes(4_270))).isEqualTo(4_270.minutes)
        }
    }
}
