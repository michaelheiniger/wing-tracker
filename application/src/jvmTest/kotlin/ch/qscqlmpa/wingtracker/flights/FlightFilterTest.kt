package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

class FlightFilterTest {
    private val flight = aFlight(details = aFlightDetails().copy(instrument = "Syride SYS'Nav XL"))
    private val wingName = "Sky Dancer"

    @Nested
    inner class IsActive {
        @Test
        fun `is not active when nothing is set`() {
            // given
            val filter = FlightFilter(text = "   ")

            // when
            val active = filter.isActive

            // then
            assertThat(active).isFalse()
        }

        @Test
        fun `is active as soon as one criterion is set`() {
            // given
            val filter = FlightFilter(to = LocalDate(2026, 9, 30))

            // when
            val active = filter.isActive

            // then
            assertThat(active).isTrue()
        }
    }

    @Nested
    inner class Matches {
        @Test
        fun `matches every flight when nothing is set`() {
            // given
            val filter = FlightFilter()

            // when
            val matches = filter.matches(flight, wingName)

            // then
            assertThat(matches).isTrue()
        }

        @Test
        fun `finds a word in the take-off site, landing site, notes, instrument or wing name`() {
            // given
            val words = listOf("niederhorn", "beatenbucht", "thermic", "syride", "dancer")

            // when
            val matching = words.filter { FlightFilter(text = it).matches(flight, wingName) }

            // then
            assertThat(matching).isEqualTo(words)
        }

        @Test
        fun `ignores case and accents on both sides`() {
            // given
            val atChateau =
                aFlight(details = aFlightDetails().copy(takeOffSite = SiteNames.of(SiteSource.Manual, "Château-d'Oex")))

            // when
            val matches = FlightFilter(text = "CHATEAU").matches(atChateau, wingName)

            // then
            assertThat(matches).isTrue()
        }

        @Test
        fun `finds a site by the name of a source that is not shown`() {
            // given
            val renamed =
                aFlight(
                    details =
                        aFlightDetails().copy(
                            takeOffSite =
                                SiteNames.of(SiteSource.Syride, "Niederhorn").with(SiteSource.Manual, "Vorsass"),
                        ),
                )

            // when
            val matches = FlightFilter(text = "niederhorn").matches(renamed, wingName)

            // then
            assertThat(matches).isTrue()
        }

        @Test
        fun `needs every word to appear, in any field`() {
            // given
            val both = FlightFilter(text = "dancer  niederhorn")
            val oneMissing = FlightFilter(text = "dancer fiesch")

            // when
            val bothMatch = both.matches(flight, wingName)
            val oneMissingMatches = oneMissing.matches(flight, wingName)

            // then
            assertThat(bothMatch).isTrue()
            assertThat(oneMissingMatches).isFalse()
        }

        @Test
        fun `does not match text when the flight has no wing name and no text fields`() {
            // given
            val bare = aFlight(details = FlightDetails(LocalDate(2026, 9, 20), 1.hours, WingId("wing-1")))

            // when
            val matches = FlightFilter(text = "niederhorn").matches(bare, wingName = null)

            // then
            assertThat(matches).isFalse()
        }

        @Test
        fun `keeps only the flights of the chosen wings`() {
            // given
            val chosen = FlightFilter(wingIds = setOf(flight.details.wingId, WingId("wing-2")))
            val other = FlightFilter(wingIds = setOf(WingId("wing-2")))

            // when
            val chosenMatches = chosen.matches(flight, wingName)
            val otherMatches = other.matches(flight, wingName)

            // then
            assertThat(chosenMatches).isTrue()
            assertThat(otherMatches).isFalse()
        }

        @Test
        fun `keeps only the flights of the chosen types, leaving out those of unknown type`() {
            // given
            val hike = aFlight(details = aFlightDetails().copy(type = FlightType.HikeAndFly))
            val unknown = aFlight(details = aFlightDetails().copy(type = null))
            val filter = FlightFilter(types = setOf(FlightType.HikeAndFly))

            // when
            val hikeMatches = filter.matches(hike, wingName)
            val unknownMatches = filter.matches(unknown, wingName)
            val siteMatches = FlightFilter(types = setOf(FlightType.Site)).matches(hike, wingName)

            // then
            assertThat(hikeMatches).isTrue()
            assertThat(unknownMatches).isFalse()
            assertThat(siteMatches).isFalse()
        }

        @Test
        fun `includes flights on the first and the last day of the range`() {
            // given
            val day = flight.details.date
            val filter = FlightFilter(from = day, to = day)

            // when
            val matches = filter.matches(flight, wingName)

            // then
            assertThat(matches).isTrue()
        }

        @Test
        fun `leaves out flights before the start or after the end of the range`() {
            // given
            val day = flight.details.date
            val later = FlightFilter(from = day.plus(1, DateTimeUnit.DAY))
            val earlier = FlightFilter(to = day.minus(1, DateTimeUnit.DAY))

            // when
            val laterMatches = later.matches(flight, wingName)
            val earlierMatches = earlier.matches(flight, wingName)

            // then
            assertThat(laterMatches).isFalse()
            assertThat(earlierMatches).isFalse()
        }
    }
}
