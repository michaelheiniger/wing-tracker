package ch.qscqlmpa.wingtracker.flights.igc

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.flights.ExternalReference
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.IgcFile
import ch.qscqlmpa.wingtracker.flights.aFlight
import ch.qscqlmpa.wingtracker.flights.aFlightDetails
import ch.qscqlmpa.wingtracker.flights.syride.SYRIDE_SYSTEM
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Real cases from a Syride logger: it names a track after the UTC time of its first fix.
private val SEPT_26 = LocalDate(2026, 9, 26)

private fun aSyrideFlight(
    id: String,
    date: LocalDate = SEPT_26,
    takeOffTime: LocalTime? = LocalTime(13, 43),
    duration: Duration = 98.minutes,
    syrideId: String = "flight:3720061",
) = aFlight(id, aFlightDetails(date = date, duration = duration, takeOffTime = takeOffTime))
    .copy(importedFrom = ExternalReference(SYRIDE_SYSTEM, syrideId))

private fun aTrack(
    fileName: String = "114344.IGC",
    firstFix: String = "2026-09-26T11:43:44Z",
    duration: Duration? = 97.minutes,
) = TrackFacts(fileName, Instant.parse(firstFix), duration)

private fun Flight.withTrack() = attachTrack(IgcFile(Attachment(AttachmentId("igc-1"), "older.igc"), "9f0c41aa77e2"))

class TrackMatcherTest {
    private val matcher = TrackMatcher(AppClock(zone = TimeZone.of("Europe/Zurich")))

    @Nested
    inner class Match {
        @Test
        fun `links a track downloaded from Syride to the flight with that Syride id`() {
            // given
            val flight = aSyrideFlight("f-1", takeOffTime = null, syrideId = "flight:3271596")

            // when
            val match = matcher.match(aTrack(fileName = "3271596.igc"), listOf(aSyrideFlight("f-0"), flight))

            // then
            assertThat(match).isEqualTo(TrackMatch.Exact(flight, MatchReason.SyrideId))
        }

        @Test
        fun `does not take a logger's time name for the number of an old Syride flight on another day`() {
            // given
            val old =
                aSyrideFlight("f-1", date = LocalDate(2012, 7, 14), takeOffTime = null, syrideId = "flight:114344")

            // when
            val match = matcher.match(aTrack(fileName = "20260926/114344.IGC", duration = 40.minutes), listOf(old))

            // then
            assertThat(match).isNull()
        }

        @Test
        fun `finds the Syride id of a downloaded track kept in a folder`() {
            // given
            val flight = aSyrideFlight("f-1", takeOffTime = null, syrideId = "flight:3271596")

            // when
            val match = matcher.match(aTrack(fileName = "Syride/3271596.igc"), listOf(flight))

            // then
            assertThat(match).isEqualTo(TrackMatch.Exact(flight, MatchReason.SyrideId))
        }

        @Test
        fun `never links to a Syride entry without a track that has the same number`() {
            // given
            val activity = aSyrideFlight("f-1", takeOffTime = null, syrideId = "untracked-flight:3271596")

            // when
            val match = matcher.match(aTrack(fileName = "3271596.igc", duration = null), listOf(activity))

            // then
            assertThat(match).isNull()
        }

        @Test
        fun `links a logger track that starts in the minute of the flight's take-off, in local time`() {
            // given
            val flight = aSyrideFlight("f-1")

            // when
            val match = matcher.match(aTrack(), listOf(flight))

            // then
            assertThat(match).isEqualTo(TrackMatch.Exact(flight, MatchReason.TakeOffTime))
        }

        @Test
        fun `links a track whose first fix is late in the minute, since Syride drops the seconds`() {
            // given
            val flight = aSyrideFlight("f-1")

            // when
            val match =
                matcher.match(
                    aTrack(fileName = "114359.IGC", firstFix = "2026-09-26T11:43:59Z"),
                    listOf(flight),
                )

            // then
            assertThat(match).isEqualTo(TrackMatch.Exact(flight, MatchReason.TakeOffTime))
        }

        @Test
        fun `tells apart several short flights of one day by their take-off minute`() {
            // given
            val flights =
                listOf(
                    aSyrideFlight("f-1", takeOffTime = LocalTime(11, 33), duration = 9.minutes),
                    aSyrideFlight("f-2", takeOffTime = LocalTime(12, 19), duration = 9.minutes),
                    aSyrideFlight("f-3", takeOffTime = LocalTime(14, 1), duration = 8.minutes),
                )

            // when
            val match =
                matcher.match(
                    aTrack(fileName = "101929.IGC", firstFix = "2026-09-26T10:19:29Z", duration = 9.minutes),
                    flights,
                )

            // then
            assertThat(match).isEqualTo(TrackMatch.Exact(flights[1], MatchReason.TakeOffTime))
        }

        @Test
        fun `compares the local date, which differs from the UTC date shortly after midnight`() {
            // given
            val flight = aSyrideFlight("f-1", date = LocalDate(2026, 9, 27), takeOffTime = LocalTime(0, 30))

            // when
            val match =
                matcher.match(
                    aTrack(fileName = "223012.IGC", firstFix = "2026-09-26T22:30:12Z"),
                    listOf(flight),
                )

            // then
            assertThat(match).isEqualTo(TrackMatch.Exact(flight, MatchReason.TakeOffTime))
        }

        @Test
        fun `offers a flight of the same day with a similar duration as a possible match only`() {
            // given
            val flight = aSyrideFlight("f-1", takeOffTime = null)

            // when
            val match = matcher.match(aTrack(), listOf(flight))

            // then
            assertThat(match).isEqualTo(TrackMatch.Possible(flight))
        }

        @Test
        fun `offers the flight with the closest duration when several are similar`() {
            // given
            val shorter = aSyrideFlight("f-1", takeOffTime = null, duration = 96.minutes)
            val closer = aSyrideFlight("f-2", takeOffTime = null, duration = 98.minutes)

            // when
            val match = matcher.match(aTrack(duration = 98.minutes), listOf(shorter, closer))

            // then
            assertThat(match).isEqualTo(TrackMatch.Possible(closer))
        }

        @Test
        fun `offers no flight whose duration differs too much`() {
            // given
            val flight = aSyrideFlight("f-1", takeOffTime = null, duration = 43.minutes)

            // when
            val match = matcher.match(aTrack(duration = 12.minutes), listOf(flight))

            // then
            assertThat(match).isNull()
        }

        @Test
        fun `does not guess a flight that already has a track`() {
            // given
            val flight = aSyrideFlight("f-1", takeOffTime = null).withTrack()

            // when
            val match = matcher.match(aTrack(), listOf(flight))

            // then
            assertThat(match).isNull()
        }

        @Test
        fun `still reports an exact match on a flight that already has a track, so the conflict can be shown`() {
            // given
            val flight = aSyrideFlight("f-1").withTrack()

            // when
            val match = matcher.match(aTrack(), listOf(flight))

            // then
            assertThat(match).isEqualTo(TrackMatch.Exact(flight, MatchReason.TakeOffTime))
        }
    }
}
