package ch.qscqlmpa.wingtracker.flights.igc

import ch.qscqlmpa.wingtracker.maps.GeoPoint
import ch.qscqlmpa.wingtracker.maps.TerrainElevationSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private fun aTrack(vararg legs: Leg) = AnalysedTrack(trackOf(*legs), flight = null, statistics = null)

class ReadGroundProfileTest {
    private val terrain = mockk<TerrainElevationSource>()
    private val readGroundProfile = ReadGroundProfile(terrain)

    @Nested
    inner class Invoke {
        @Test
        fun `gives the ground under each fix of a short track`() =
            runTest {
                // given
                val track = aTrack(Leg.Moving(1.minutes, 32.0))
                coEvery { terrain.elevations(any()) } answers {
                    firstArg<List<GeoPoint>>().indices.map { 1950 - it * 10 }
                }

                // when
                val profile = readGroundProfile(track)

                // then
                assertThat(profile.complete).isTrue()
                assertThat(profile.points).hasSize(track.fixes.size)
                assertThat(profile.points.first()).isEqualTo(GroundPoint(at(0.minutes), 1950))
            }

        @Test
        fun `samples a long track, keeping its first and last fix`() =
            runTest {
                // given
                val track = aTrack(Leg.Moving(180.minutes, 32.0))
                coEvery { terrain.elevations(any()) } answers { firstArg<List<GeoPoint>>().map { 1200 } }

                // when
                val profile = readGroundProfile(track)

                // then
                assertThat(profile.points).hasSize(600)
                assertThat(profile.points.first().time).isEqualTo(track.fixes.first().time)
                assertThat(profile.points.last().time).isEqualTo(track.fixes.last().time)
            }

        @Test
        fun `is incomplete where the terrain could not be had`() =
            runTest {
                // given
                val track = aTrack(Leg.Moving(10.seconds, 32.0))
                coEvery { terrain.elevations(any()) } returns listOf(1950, null, 1930)

                // when
                val profile = readGroundProfile(track)

                // then
                assertThat(profile.complete).isFalse()
                assertThat(profile.points.map { it.elevationMeters }).containsExactly(1950, 1930)
            }
    }

    @Nested
    inner class ElevationAt {
        private val profile =
            GroundProfile(listOf(GroundPoint(at(0.minutes), 1950), GroundPoint(at(1.minutes), 1890)), complete = true)

        @Test
        fun `interpolates between the two nearest points`() {
            // given / when
            val elevation = profile.elevationAt(at(20.seconds))

            // then
            assertThat(elevation).isCloseTo(1930.0, within(0.01))
        }

        @Test
        fun `has nothing outside the profile`() {
            // given / when / then
            assertThat(profile.elevationAt(at(2.minutes))).isNull()
            assertThat(profile.elevationAt(at((-1).minutes))).isNull()
        }
    }
}
