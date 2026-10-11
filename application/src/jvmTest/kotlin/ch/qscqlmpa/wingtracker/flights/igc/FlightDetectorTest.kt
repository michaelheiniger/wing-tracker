package ch.qscqlmpa.wingtracker.flights.igc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val TOLERANCE = 30.seconds

private fun still(minutes: Int) = Leg.Still(minutes.minutes)

private fun walk(minutes: Int) = Leg.Moving(minutes.minutes, speedKmh = 4.0)

private fun fly(minutes: Int) = Leg.Moving(minutes.minutes, speedKmh = 32.0)

private fun drive(minutes: Int) = Leg.Moving(minutes.minutes, speedKmh = 60.0)

class FlightDetectorTest {
    @Nested
    inner class Detection {
        @Test
        fun `finds the flight in a recording that started before take-off and ended after landing`() {
            // given
            val fixes = trackOf(still(10), fly(75), still(8))

            // when
            val flight = detectFlight(fixes)!!

            // then
            assertThat(flight.takeOff).isBetween(at(10.minutes - TOLERANCE), at(10.minutes + TOLERANCE))
            assertThat(flight.landing).isBetween(at(85.minutes - TOLERANCE), at(85.minutes + TOLERANCE))
            assertThat(flight.durationInMinutes).isBetween(74.minutes, 76.minutes)
        }

        @Test
        fun `does not count the walk up to the launch as flight time`() {
            // given
            val fixes = trackOf(walk(40), still(3), fly(60), still(6))

            // when
            val flight = detectFlight(fixes)!!

            // then
            assertThat(flight.takeOff).isBetween(at(43.minutes - TOLERANCE), at(43.minutes + TOLERANCE))
            assertThat(flight.durationInMinutes).isBetween(59.minutes, 61.minutes)
        }

        @Test
        fun `ignores a car ride after the pilot stood still for a while`() {
            // given
            val fixes = trackOf(still(2), fly(45), still(12), drive(20))

            // when
            val flight = detectFlight(fixes)!!

            // then
            assertThat(flight.landing).isBetween(at(47.minutes - TOLERANCE), at(47.minutes + TOLERANCE))
        }

        @Test
        fun `keeps one flight together across a short hover`() {
            // given
            val fixes = trackOf(still(2), fly(30), still(1), fly(30), still(8))

            // when
            val flight = detectFlight(fixes)!!

            // then
            assertThat(flight.durationInMinutes).isBetween(60.minutes, 62.minutes)
        }

        @Test
        fun `reports only the first flight when the pilot launched again after a long pause`() {
            // given
            val fixes = trackOf(still(2), fly(40), still(15), fly(30), still(3))

            // when
            val flight = detectFlight(fixes)!!

            // then
            assertThat(flight.durationInMinutes).isBetween(39.minutes, 41.minutes)
        }
    }

    @Nested
    inner class NoFlight {
        @Test
        fun `finds nothing in a recording that never moves`() {
            assertThat(detectFlight(trackOf(still(30)))).isNull()
        }

        @Test
        fun `finds nothing when the pilot only walks`() {
            assertThat(detectFlight(trackOf(walk(45)))).isNull()
        }

        @Test
        fun `finds nothing in a movement shorter than a minute`() {
            assertThat(detectFlight(trackOf(still(2), Leg.Moving(30.seconds, 32.0), still(5)))).isNull()
        }

        @Test
        fun `finds nothing in an empty or single-fix track`() {
            assertThat(detectFlight(emptyList())).isNull()
            assertThat(detectFlight(trackOf())).isNull()
        }
    }

    @Nested
    inner class Rounding {
        @Test
        fun `rounds the duration to the nearest whole minute`() {
            // given
            val up = DetectedFlight(at(0.seconds), at(89.minutes + 40.seconds))
            val down = DetectedFlight(at(0.seconds), at(89.minutes + 20.seconds))

            // when / then
            assertThat(up.durationInMinutes).isEqualTo(90.minutes)
            assertThat(down.durationInMinutes).isEqualTo(89.minutes)
        }
    }
}
