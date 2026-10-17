package ch.qscqlmpa.wingtracker.ui.flights.detail

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.IgcFile
import ch.qscqlmpa.wingtracker.flights.igc.AnalysedTrack
import ch.qscqlmpa.wingtracker.flights.igc.GroundPoint
import ch.qscqlmpa.wingtracker.flights.igc.GroundProfile
import ch.qscqlmpa.wingtracker.flights.igc.InvalidTrackException
import ch.qscqlmpa.wingtracker.flights.igc.ReadFlightTrack
import ch.qscqlmpa.wingtracker.flights.igc.ReadGroundProfile
import ch.qscqlmpa.wingtracker.flights.igc.TrackFix
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.ui.FailureKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private fun aFlight(track: IgcFile? = IgcFile(Attachment(AttachmentId("igc-1"), "114344.IGC"), "9f0c41aa77e2")) =
    Flight(
        id = FlightId("f-1"),
        details = FlightDetails(date = LocalDate(2026, 9, 20), duration = 95.minutes, wingId = WingId("wing-1")),
        loggedAt = Instant.parse("2026-09-20T17:00:00Z"),
        igcFile = track,
    )

private val A_TRACK =
    AnalysedTrack(
        fixes = listOf(TrackFix(Instant.parse("2026-09-20T09:00:00Z"), 46.7, 7.8, gpsAltitudeMeters = 1950)),
        flight = null,
        statistics = null,
    )

private val GROUND =
    GroundProfile(listOf(GroundPoint(Instant.parse("2026-09-20T09:00:00Z"), 1950)), complete = true)

class FlightTrackPresenterTest {
    private val readFlightTrack = mockk<ReadFlightTrack>()
    private val readGroundProfile = mockk<ReadGroundProfile>()
    private val presenter = FlightTrackPresenter(readFlightTrack, readGroundProfile)

    init {
        coEvery { readGroundProfile(any()) } returns GROUND
    }

    @Nested
    inner class Load {
        @Test
        fun `shows the track of the flight`() =
            runTest {
                // given
                val flight = aFlight()
                coEvery { readFlightTrack(flight) } returns A_TRACK

                // when
                presenter.load(flight)

                // then
                assertThat(presenter.state).isEqualTo(TrackState.Loaded(A_TRACK, GROUND))
            }

        @Test
        fun `reads the ground under the track once the track is shown`() =
            runTest {
                // given
                val flight = aFlight()
                coEvery { readFlightTrack(flight) } returns A_TRACK

                // when
                presenter.load(flight)

                // then
                assertThat((presenter.state as TrackState.Loaded).ground).isEqualTo(GROUND)
            }

        @Test
        fun `shows the track without ground when the ground cannot be read`() =
            runTest {
                // given
                val flight = aFlight()
                coEvery { readFlightTrack(flight) } returns A_TRACK
                coEvery { readGroundProfile(A_TRACK) } throws IllegalStateException("Disk full")

                // when
                presenter.load(flight)

                // then
                val loaded = presenter.state as TrackState.Loaded
                assertThat(loaded.track).isEqualTo(A_TRACK)
                assertThat(loaded.ground).isNull()
                assertThat(loaded.groundFailure?.kind).isEqualTo(FailureKind.ReadGround)
            }

        @Test
        fun `has no track to show and reads nothing for a flight without one`() =
            runTest {
                // given
                val flight = aFlight(track = null)

                // when
                presenter.load(flight)

                // then
                assertThat(presenter.state).isEqualTo(TrackState.None)
                coVerify(exactly = 0) { readFlightTrack(any()) }
            }

        @Test
        fun `reports a track that cannot be read`() =
            runTest {
                // given
                val flight = aFlight()
                coEvery { readFlightTrack(flight) } throws InvalidTrackException("The file contains no position fixes")

                // when
                presenter.load(flight)

                // then
                val failed = presenter.state as TrackState.Failed
                assertThat(failed.failure.kind).isEqualTo(FailureKind.ReadTrack)
            }

        @Test
        fun `forgets the track of the flight shown before`() =
            runTest {
                // given
                val tracked = aFlight()
                coEvery { readFlightTrack(tracked) } returns A_TRACK
                presenter.load(tracked)

                // when
                presenter.load(aFlight(track = null))

                // then
                assertThat(presenter.state).isEqualTo(TrackState.None)
            }
    }
}
