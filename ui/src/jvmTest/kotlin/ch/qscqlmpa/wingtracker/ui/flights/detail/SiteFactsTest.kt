package ch.qscqlmpa.wingtracker.ui.flights.detail

import ch.qscqlmpa.wingtracker.maps.GeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SiteFactsTest {
    @Nested
    inner class PositionToDisplay {
        @Test
        fun `gives signed decimal degrees to five decimals, latitude first`() {
            // given
            val niederhorn = GeoPoint(46.7103871, 7.7771249)

            // when
            val shown = niederhorn.toDisplay()

            // then
            assertThat(shown).isEqualTo("46.71039, 7.77712")
        }

        @Test
        fun `keeps the leading zeros of the decimals and the sign west and south of zero`() {
            // given
            val position = GeoPoint(-33.0500001, -0.0012)

            // when
            val shown = position.toDisplay()

            // then
            assertThat(shown).isEqualTo("-33.05000, -0.00120")
        }
    }
}
