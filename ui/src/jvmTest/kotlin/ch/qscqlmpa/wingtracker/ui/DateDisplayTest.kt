package ch.qscqlmpa.wingtracker.ui

import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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

    @Nested
    inner class ParseDate {
        @Test
        fun `reads day, month and year as they are displayed`() {
            // given
            val text = "09-03-2024"

            // when
            val date = parseDate(text)

            // then
            assertThat(date).isEqualTo(LocalDate(2024, 3, 9))
        }

        @ParameterizedTest
        @ValueSource(strings = ["9.3.2024", "09/03/2024", " 9-3-2024 "])
        fun `accepts dots, slashes, single digits and surrounding spaces`(text: String) {
            // when
            val date = parseDate(text)

            // then
            assertThat(date).isEqualTo(LocalDate(2024, 3, 9))
        }

        @ParameterizedTest
        @ValueSource(strings = ["31-02-2024", "09-13-2024", "09-03-24", "09-03/2024", "2024-03-09", "", "March 9"])
        fun `rejects text that is not a real day written day first with a full year`(text: String) {
            // when
            val date = parseDate(text)

            // then
            assertThat(date).isNull()
        }
    }
}
