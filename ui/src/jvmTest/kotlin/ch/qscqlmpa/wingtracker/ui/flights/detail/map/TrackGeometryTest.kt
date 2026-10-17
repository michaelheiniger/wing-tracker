package ch.qscqlmpa.wingtracker.ui.flights.detail.map

import ch.qscqlmpa.wingtracker.flights.igc.AnalysedTrack
import ch.qscqlmpa.wingtracker.flights.igc.DetectedFlight
import ch.qscqlmpa.wingtracker.flights.igc.TrackFix
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private fun fixAt(
    time: String,
    latitude: Double = 46.7,
    longitude: Double = 7.8,
) = TrackFix(Instant.parse(time), latitude, longitude, gpsAltitudeMeters = 1950)

private val WAITING = fixAt("2026-09-20T09:00:00Z")
private val TAKE_OFF = fixAt("2026-09-20T09:10:00Z", longitude = 7.80)
private val FLYING = fixAt("2026-09-20T09:40:00Z", latitude = 46.68, longitude = 7.85)
private val LANDING = fixAt("2026-09-20T10:10:00Z", latitude = 46.69, longitude = 7.82)
private val PACKING = fixAt("2026-09-20T10:20:00Z", latitude = 46.69, longitude = 7.82)

private fun aTrack(flight: DetectedFlight? = DetectedFlight(TAKE_OFF.time, LANDING.time)) =
    AnalysedTrack(listOf(WAITING, TAKE_OFF, FLYING, LANDING, PACKING), flight, statistics = null)

class TrackGeometryTest {
    @Nested
    inner class ToMapPoint {
        @Test
        fun `puts the crossing of the equator and Greenwich at the centre of the map`() {
            // given
            val fix = fixAt("2026-09-20T09:00:00Z", latitude = 0.0, longitude = 0.0)

            // when
            val point = fix.toMapPoint()

            // then
            assertThat(point.x).isCloseTo(0.5, within(1e-9))
            assertThat(point.y).isCloseTo(0.5, within(1e-9))
        }

        @Test
        fun `places Niederhorn in its tile at zoom level 13`() {
            // given
            val tilesPerSide = 1 shl 13

            // when
            val point = fixAt("2026-09-20T09:00:00Z", latitude = 46.71, longitude = 7.77).toMapPoint()

            // then
            assertThat((point.x * tilesPerSide).toInt()).isEqualTo(4272)
            assertThat((point.y * tilesPerSide).toInt()).isEqualTo(2890)
        }
    }

    @Nested
    inner class Segments {
        @Test
        fun `splits the recording at take-off and landing without a gap`() {
            // given
            val track = aTrack()

            // when
            val segments = track.segments()

            // then
            assertThat(segments.before).containsExactly(WAITING, TAKE_OFF)
            assertThat(segments.flown).containsExactly(TAKE_OFF, FLYING, LANDING)
            assertThat(segments.after).containsExactly(LANDING, PACKING)
        }

        @Test
        fun `has only the ground part when the track shows no flight`() {
            // given
            val track = aTrack(flight = null)

            // when
            val segments = track.segments()

            // then
            assertThat(segments.before).hasSize(5)
            assertThat(segments.flown).isEmpty()
            assertThat(segments.after).isEmpty()
        }
    }

    @Nested
    inner class FocusArea {
        @Test
        fun `frames the flight, not the recording around it`() {
            // given
            val track = aTrack()

            // when
            val area = checkNotNull(track.focusArea())

            // then
            assertThat(area.left).isEqualTo(TAKE_OFF.toMapPoint().x)
            assertThat(area.right).isEqualTo(FLYING.toMapPoint().x)
            assertThat(area.top).isEqualTo(TAKE_OFF.toMapPoint().y)
            assertThat(area.bottom).isEqualTo(FLYING.toMapPoint().y)
        }

        @Test
        fun `frames the whole recording when the track shows no flight`() {
            // given
            val track = aTrack(flight = null)

            // when
            val area = checkNotNull(track.focusArea())

            // then
            assertThat(area.right).isEqualTo(FLYING.toMapPoint().x)
            assertThat(area.left).isEqualTo(WAITING.toMapPoint().x)
        }
    }
}
