package ch.qscqlmpa.wingtracker.flights.igc

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.IgcFile
import ch.qscqlmpa.wingtracker.flights.aFixedClock
import ch.qscqlmpa.wingtracker.flights.aFlight
import ch.qscqlmpa.wingtracker.maps.GeoPoint
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val TRACK_ID = AttachmentId("igc-1")
private val BYTES = "the stored track".encodeToByteArray()

private fun aTrackedFlight() =
    aFlight().attachTrack(IgcFile(Attachment(TRACK_ID, "2026-09-20-niederhorn.igc"), "9f0c41aa77e2"))

class ReadFlightTrackTest {
    private val attachments = mockk<AttachmentStore>()
    private val reader = mockk<TrackReader>()
    private val readFlightTrack = ReadFlightTrack(attachments, reader, aFixedClock())

    // Returns what the detector finds, since detecting take-off and landing is tested on its own.
    private fun givenTrack(vararg legs: Leg): DetectedFlight? {
        val fixes = trackOf(*legs)
        coEvery { attachments.read(TRACK_ID) } returns BYTES
        every { reader.read(BYTES) } returns ReadTrack(FlightTrack(LocalDate(2026, 9, 20), fixes), "9f0c41aa77e2")
        return detectFlight(fixes)
    }

    @Nested
    inner class Invoke {
        @Test
        fun `has nothing for a flight without a track`() =
            runTest {
                // given
                val flight = aFlight()

                // when
                val track = readFlightTrack(flight)

                // then
                assertThat(track).isNull()
            }

        @Test
        fun `keeps the whole recording and marks the part flown`() =
            runTest {
                // given
                val detected = givenTrack(Leg.Still(10.minutes), Leg.Moving(60.minutes, 32.0), Leg.Still(10.minutes))

                // when
                val track = checkNotNull(readFlightTrack(aTrackedFlight()))

                // then
                assertThat(track.fixes.first().time).isEqualTo(at(0.minutes))
                assertThat(track.fixes.last().time).isEqualTo(at(80.minutes))
                assertThat(track.flight).isEqualTo(detected)
                assertThat(track.flown.first().time).isEqualTo(detected?.takeOff)
                assertThat(track.flown.last().time).isEqualTo(detected?.landing)
            }

        @Test
        fun `gives the local take-off and landing times and the altitudes of the flight`() =
            runTest {
                // given
                val detected =
                    givenTrack(
                        Leg.Still(10.minutes),
                        Leg.Moving(20.minutes, 32.0, climbMetersPerSecond = 1.0),
                        Leg.Moving(40.minutes, 32.0, climbMetersPerSecond = -1.0),
                        Leg.Still(10.minutes),
                    )

                // when
                val statistics = checkNotNull(readFlightTrack(aTrackedFlight())?.statistics)

                // then
                val local = { instant: Instant -> instant.toLocalDateTime(TimeZone.UTC).time }
                assertThat(statistics.takeOffTime).isEqualTo(local(checkNotNull(detected).takeOff))
                assertThat(statistics.landingTime).isEqualTo(local(detected.landing))
                assertThat(statistics.takeOffAltitudeMeters).isEqualTo(START_ALTITUDE)
                assertThat(statistics.maxAltitudeMeters).isEqualTo(START_ALTITUDE + 1200)
                assertThat(statistics.landingAltitudeMeters).isEqualTo(START_ALTITUDE - 1200)
            }

        @Test
        fun `gives the positions of take-off and landing`() =
            runTest {
                // given
                givenTrack(Leg.Still(10.minutes), Leg.Moving(60.minutes, 32.0), Leg.Still(10.minutes))

                // when
                val track = checkNotNull(readFlightTrack(aTrackedFlight()))

                // then
                val statistics = checkNotNull(track.statistics)
                val takeOff = track.flown.first()
                val landing = track.flown.last()
                assertThat(statistics.takeOffPosition).isEqualTo(GeoPoint(takeOff.latitude, takeOff.longitude))
                assertThat(statistics.landingPosition).isEqualTo(GeoPoint(landing.latitude, landing.longitude))
                assertThat(statistics.landingPosition).isNotEqualTo(statistics.takeOffPosition)
            }

        @Test
        fun `averages the best climb and the strongest sink`() =
            runTest {
                // given
                givenTrack(
                    Leg.Still(10.minutes),
                    Leg.Moving(10.minutes, 32.0, climbMetersPerSecond = 2.5),
                    Leg.Moving(10.minutes, 32.0, climbMetersPerSecond = -1.5),
                    Leg.Still(10.minutes),
                )

                // when
                val statistics = checkNotNull(readFlightTrack(aTrackedFlight())?.statistics)

                // then
                assertThat(statistics.bestClimbMetersPerSecond).isCloseTo(2.5, within(0.01))
                assertThat(statistics.maxSinkMetersPerSecond).isCloseTo(-1.5, within(0.01))
            }

        @Test
        fun `gives the climb rate along the flight, so the map can colour it`() =
            runTest {
                // given
                givenTrack(
                    Leg.Still(10.minutes),
                    Leg.Moving(10.minutes, 32.0, climbMetersPerSecond = 2.5),
                    Leg.Moving(10.minutes, 32.0, climbMetersPerSecond = -1.5),
                    Leg.Still(10.minutes),
                )

                // when
                val track = checkNotNull(readFlightTrack(aTrackedFlight()))

                // then
                val climbing = track.climbRates.first { it.from == at(15.minutes) }
                val sinking = track.climbRates.first { it.from == at(25.minutes) }
                assertThat(climbing.metersPerSecond).isCloseTo(2.5, within(0.01))
                assertThat(sinking.metersPerSecond).isCloseTo(-1.5, within(0.01))
                assertThat(track.climbRates.map { it.from }).isSorted()
            }

        @Test
        fun `has no altitudes when the logger recorded none`() =
            runTest {
                // given
                coEvery { attachments.read(TRACK_ID) } returns BYTES
                val withoutAltitudes =
                    trackOf(Leg.Still(10.minutes), Leg.Moving(30.minutes, 32.0), Leg.Still(10.minutes))
                        .map { it.copy(gpsAltitudeMeters = null) }
                every { reader.read(BYTES) } returns
                    ReadTrack(FlightTrack(LocalDate(2026, 9, 20), withoutAltitudes), "9f0c41aa77e2")

                // when
                val statistics = checkNotNull(readFlightTrack(aTrackedFlight())?.statistics)

                // then
                assertThat(statistics.maxAltitudeMeters).isNull()
                assertThat(statistics.bestClimbMetersPerSecond).isNull()
                assertThat(statistics.maxSinkMetersPerSecond).isNull()
            }

        @Test
        fun `shows a track that never moves, without statistics`() =
            runTest {
                // given
                givenTrack(Leg.Still(30.minutes))

                // when
                val track = checkNotNull(readFlightTrack(aTrackedFlight()))

                // then
                assertThat(track.fixes).isNotEmpty()
                assertThat(track.flight).isNull()
                assertThat(track.flown).isEmpty()
                assertThat(track.statistics).isNull()
            }

        @Test
        fun `fails when the stored file is no longer a readable track`() =
            runTest {
                // given
                coEvery { attachments.read(TRACK_ID) } returns BYTES
                every { reader.read(BYTES) } throws InvalidTrackException("The file contains no position fixes")

                // when
                val failure = runCatching { readFlightTrack(aTrackedFlight()) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(InvalidTrackException::class.java)
            }
    }
}
