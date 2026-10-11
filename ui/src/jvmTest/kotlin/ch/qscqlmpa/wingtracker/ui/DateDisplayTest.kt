package ch.qscqlmpa.wingtracker.ui

import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DateDisplayTest {
    @Nested
    inner class ToDisplay {
        @Test
        fun `shows day, month and year in that order with zero padding`() {
            // given
            val date = LocalDate(2024, 3, 9)

            // when
            val text = date.toDisplay()

            // then
            assertThat(text).isEqualTo("09-03-2024")
        }

        @Test
        fun `keeps two-digit days and months as they are`() {
            // given
            val date = LocalDate(2026, 10, 11)

            // when
            val text = date.toDisplay()

            // then
            assertThat(text).isEqualTo("11-10-2026")
        }
    }
}
