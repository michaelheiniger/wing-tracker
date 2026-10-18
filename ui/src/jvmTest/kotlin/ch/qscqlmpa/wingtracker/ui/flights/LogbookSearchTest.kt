package ch.qscqlmpa.wingtracker.ui.flights

import ch.qscqlmpa.wingtracker.flights.FlightType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LogbookSearchTest {
    @Nested
    inner class IsRangeReversed {
        @Test
        fun `is reversed when From is after To`() {
            // given
            val search = LogbookSearch(from = "02-10-2026", to = "01-10-2026")

            // when
            val reversed = search.isRangeReversed

            // then
            assertThat(reversed).isTrue()
        }

        @Test
        fun `is not reversed when both are the same day`() {
            // given
            val search = LogbookSearch(from = "01-10-2026", to = "01-10-2026")

            // when
            val reversed = search.isRangeReversed

            // then
            assertThat(reversed).isFalse()
        }

        @Test
        fun `is not reversed while one of the dates is not a real day yet`() {
            // given
            val search = LogbookSearch(from = "02-10-2026", to = "01-10-20")

            // when
            val reversed = search.isRangeReversed

            // then
            assertThat(reversed).isFalse()
        }
    }

    @Nested
    inner class ToggleType {
        @Test
        fun `adds a type to the search and removes it again`() {
            // given
            val search = LogbookSearch()

            // when
            val added = search.toggle(FlightType.HikeAndFly)
            val removed = added.toggle(FlightType.HikeAndFly)

            // then
            assertThat(added.toFilter().types).containsExactly(FlightType.HikeAndFly)
            assertThat(added.isBlank).isFalse()
            assertThat(removed.isBlank).isTrue()
        }
    }
}
