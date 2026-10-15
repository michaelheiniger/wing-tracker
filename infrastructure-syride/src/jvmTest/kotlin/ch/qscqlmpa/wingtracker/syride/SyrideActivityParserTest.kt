package ch.qscqlmpa.wingtracker.syride

import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import ch.qscqlmpa.wingtracker.flights.syride.EarlierReading
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
            assertThat(flight.wing).isEqualTo(SourceWing("Hiko P", "Niviuk"))
        }

        @Test
        fun `reads distance, altitude and instrument`() {
            assertThat(flight.distanceKm).isEqualTo(15.0)
            assertThat(flight.topAltitudeMeters).isEqualTo(2464)
            assertThat(flight.instrument).isEqualTo("Nav XL")
        }

        @Test
        fun `carries no earlier reading when the earlier reader got it right`() {
            assertThat(flight.earlierReading).isNull()
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
            assertThat(flight.wing).isEqualTo(SourceWing("Ultralite 4", "Ozone"))
            assertThat(flight.duration).isEqualTo(10.minutes)
        }
    }

    @Nested
    inner class Text {
        // As Syride writes them: accents as named entities, and parentheses that belong to the name.
        private val card =
            "<div id='activite3770736' class=\"boxno\"><div id=flightStatsNoSys>" +
                "<ul>Flying site : <font id='site3523881'>Sonchaux (d&eacute;co nord)</font></ul>" +
                "<ul>Flight name : <font id='nom3523881'>Open de Jaman (RVL)</font></ul>" +
                "<ul>Date : <font>31/05/2026 - 11h35</font></ul><ul>Flight time : <font>10min</font></ul>" +
                "<ul id='voile3523881'>Used wing : <font>Ultralite 4</font> (Ozone <img src='/v/24.gif'>)</ul>" +
                "</div></div>"

        private val flight get() = SyrideActivityParser.parsePage(card).flights.single()

        @Test
        fun `decodes accents written as named entities`() {
            assertThat(flight.siteName).isEqualTo("Sonchaux (déco nord)")
        }

        @Test
        fun `keeps parentheses that belong to the name`() {
            assertThat(flight.name).isEqualTo("Open de Jaman (RVL)")
            assertThat(flight.wing).isEqualTo(SourceWing("Ultralite 4", "Ozone"))
        }

        @Test
        fun `remembers how the earlier reader garbled them`() {
            assertThat(flight.earlierReading).isEqualTo(EarlierReading(name = "Open de Jaman", siteName = "Sonchaux"))
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

        @Test
        fun `counts a flight card whose fields changed markup instead of skipping it`() {
            // given
            val changed =
                "<div id='activite1' class=\"boxno\"><li>Flight name : <font>X</font></li>" +
                    "<li>Date : <font>26/09/2026 - 13h43</font></li></div>"

            // when
            val page = SyrideActivityParser.parsePage(changed)

            // then
            assertThat(page.flights).isEmpty()
            assertThat(page.unreadableFlights).isEqualTo(1)
        }
    }
}
