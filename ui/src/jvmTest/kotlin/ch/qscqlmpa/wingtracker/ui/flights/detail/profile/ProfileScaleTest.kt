package ch.qscqlmpa.wingtracker.ui.flights.detail.profile

import ch.qscqlmpa.wingtracker.flights.igc.TrackFix
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private fun at(time: String) = Instant.parse("2026-10-08T${time}Z")

private fun fixAt(time: String) = TrackFix(at(time), 46.7, 7.8, gpsAltitudeMeters = 1998)

class ProfileScaleTest {
    @Nested
    inner class TimeTicks {
        @Test
        fun `uses round quarter hours for a flight of an hour and a quarter`() {
            // given
            val start = at("11:09:40")
            val end = at("12:25:10")

            // when
            val ticks = timeTicks(start, end, maxTicks = 6)

            // then
            assertThat(
                ticks,
            ).containsExactly(at("11:15:00"), at("11:30:00"), at("11:45:00"), at("12:00:00"), at("12:15:00"))
        }

        @Test
        fun `uses whole hours for a long cross-country flight`() {
            // given
            val start = at("09:40:00")
            val end = at("15:10:00")

            // when
            val ticks = timeTicks(start, end, maxTicks = 6)

            // then
            assertThat(
                ticks,
            ).containsExactly(
                at("10:00:00"),
                at("11:00:00"),
                at("12:00:00"),
                at("13:00:00"),
                at("14:00:00"),
                at("15:00:00"),
            )
        }

        @Test
        fun `keeps a tick that falls on the start`() {
            // given
            val start = at("11:00:00")
            val end = at("11:20:00")

            // when
            val ticks = timeTicks(start, end, maxTicks = 6)

            // then
            assertThat(ticks.first()).isEqualTo(start)
        }
    }

    @Nested
    inner class NearestTo {
        private val fixes = listOf(fixAt("11:00:00"), fixAt("11:00:05"), fixAt("11:00:10"))

        @Test
        fun `finds the fix closest in time`() {
            // given
            val time = at("11:00:06")

            // when
            val nearest = fixes.nearestTo(time)

            // then
            assertThat(nearest).isEqualTo(fixes[1])
        }

        @Test
        fun `finds the first or last fix for a time outside the track`() {
            // given / when
            val before = fixes.nearestTo(at("10:59:00"))
            val after = fixes.nearestTo(at("11:05:00"))

            // then
            assertThat(before).isEqualTo(fixes.first())
            assertThat(after).isEqualTo(fixes.last())
        }

        @Test
        fun `has nothing for a track without fixes`() {
            // given / when / then
            assertThat(emptyList<TrackFix>().nearestTo(at("11:00:00"))).isNull()
        }
    }
}
