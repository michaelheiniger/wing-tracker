package ch.qscqlmpa.wingtracker.syride

import ch.qscqlmpa.wingtracker.flights.syride.SyrideWing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

// Real cards saved from a public profile: a tracked flight, a flight logged without instrument, and a photo post.
private val SAMPLE = SyrideActivityParserTest::class.java.getResource("/activities-sample.html")!!.readText()

class SyrideActivityParserTest {
    @Nested
    inner class ReadingAPage {
        @Test
        fun `finds every card and only the flights among them`() {
            // when
            val page = SyrideActivityParser.parsePage(SAMPLE)

            // then
            assertThat(page.activityIds).hasSize(3)
            assertThat(page.flights).hasSize(2)
            assertThat(page.unreadableFlights).isZero()
        }

        @Test
        fun `returns nothing for an empty page`() {
            assertThat(SyrideActivityParser.parsePage(" \n").activityIds).isEmpty()
        }
    }

    @Nested
    inner class ATrackedFlight {
        private val flight get() = SyrideActivityParser.parsePage(SAMPLE).flights.first { it.hasTrack }

        @Test
        fun `reads when, how long and where`() {
            assertThat(flight.date).isEqualTo(LocalDate(2026, 9, 26))
            assertThat(flight.startTime).isEqualTo(LocalTime(13, 43))
            assertThat(flight.duration).isEqualTo(98.minutes)
            assertThat(flight.siteName).isEqualTo("Dent De Jaman")
            assertThat(flight.name).isEqualTo("Course Tour du Bocal vol 3")
        }

        @Test
        fun `reads the wing with its brand`() {
            assertThat(flight.wing).isEqualTo(SyrideWing("Hiko P", "Niviuk"))
        }

        @Test
        fun `reads distance, altitude and instrument`() {
            assertThat(flight.distanceKm).isEqualTo(15.0)
            assertThat(flight.topAltitudeMeters).isEqualTo(2464)
            assertThat(flight.instrument).isEqualTo("Nav XL")
        }

        @Test
        fun `identifies the flight by its Syride flight id`() {
            assertThat(flight.id).isEqualTo("flight:3720061")
        }
    }

    @Nested
    inner class AFlightWithoutTrack {
        private val flight get() = SyrideActivityParser.parsePage(SAMPLE).flights.first { !it.hasTrack }

        @Test
        fun `has no track, distance, altitude or instrument`() {
            assertThat(flight.distanceKm).isNull()
            assertThat(flight.topAltitudeMeters).isNull()
            assertThat(flight.instrument).isNull()
        }

        @Test
        fun `is identified by its activity and has no flight name when none was set`() {
            assertThat(flight.id).startsWith("activity:")
            assertThat(flight.name).isNull()
        }

        @Test
        fun `reads the site from the plain label`() {
            assertThat(flight.siteName).isEqualTo("Sonchaux")
            assertThat(flight.wing).isEqualTo(SyrideWing("Ultralite 4", "Ozone"))
            assertThat(flight.duration).isEqualTo(10.minutes)
        }
    }

    @Nested
    inner class AChangedPage {
        @Test
        fun `counts a flight card it cannot read instead of failing`() {
            // given
            val broken = "<div id='activite1' class=\"boxno\"><ul>Flight name : <font>X</font></ul></div>"

            // when
            val page = SyrideActivityParser.parsePage(broken)

            // then
            assertThat(page.flights).isEmpty()
            assertThat(page.unreadableFlights).isEqualTo(1)
        }
    }
}
