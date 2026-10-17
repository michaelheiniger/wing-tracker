package ch.qscqlmpa.wingtracker.ui.flights.detail.map

import ch.qscqlmpa.wingtracker.flights.igc.AnalysedTrack
import ch.qscqlmpa.wingtracker.flights.igc.ClimbRate
import ch.qscqlmpa.wingtracker.flights.igc.DetectedFlight
import ch.qscqlmpa.wingtracker.flights.igc.TrackFix
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.time.Instant

private fun at(time: String) = Instant.parse("2026-10-08T${time}Z")

private fun fixAt(time: String) = TrackFix(at(time), 46.7, 7.8, gpsAltitudeMeters = 1998)

private val FIXES = listOf("11:10:00", "11:10:10", "11:10:20", "11:10:30", "11:10:40").map { fixAt(it) }

private fun aTrack(climbRates: List<ClimbRate>) =
    AnalysedTrack(
        FIXES,
        DetectedFlight(FIXES.first().time, FIXES.last().time),
        statistics = null,
        climbRates = climbRates,
    )

class ClimbBandsTest {
    @Nested
    inner class ClimbBandOf {
        @ParameterizedTest(name = "{0} m/s is {1}")
        @CsvSource(
            "-3.2, StrongSink",
            "-1.21, StrongSink",
            "-1.2, WeakSink",
            "-0.1, WeakSink",
            "0.0, WeakClimb",
            "0.9, WeakClimb",
            "1.0, ModerateClimb",
            "2.4, ModerateClimb",
            "2.5, StrongClimb",
            "4.8, StrongClimb",
        )
        fun `puts a climb rate in its band`(
            metersPerSecond: Double,
            band: ClimbBand,
        ) {
            // given / when / then
            assertThat(climbBandOf(metersPerSecond)).isEqualTo(band)
        }
    }

    @Nested
    inner class ClimbRuns {
        @Test
        fun `cuts the flight where the band changes, sharing the fix at the cut`() {
            // given
            val track =
                aTrack(
                    listOf(
                        ClimbRate(at("11:10:00"), -1.1),
                        ClimbRate(at("11:10:10"), -0.8),
                        ClimbRate(at("11:10:20"), 2.7),
                        ClimbRate(at("11:10:30"), 3.1),
                    ),
                )

            // when
            val runs = track.climbRuns()

            // then
            assertThat(runs.map { it.band }).containsExactly(ClimbBand.WeakSink, ClimbBand.StrongClimb)
            assertThat(runs[0].fixes).containsExactly(FIXES[0], FIXES[1], FIXES[2])
            assertThat(runs[1].fixes).containsExactly(FIXES[2], FIXES[3], FIXES[4])
        }

        @Test
        fun `gives the last fixes the last rate measured before them`() {
            // given
            val track = aTrack(listOf(ClimbRate(at("11:10:00"), 1.4)))

            // when
            val runs = track.climbRuns()

            // then
            assertThat(runs).containsExactly(ClimbRun(ClimbBand.ModerateClimb, FIXES))
        }

        @Test
        fun `has nothing to colour without climb rates`() {
            // given
            val track = aTrack(emptyList())

            // when
            val runs = track.climbRuns()

            // then
            assertThat(runs).isEmpty()
        }
    }
}
