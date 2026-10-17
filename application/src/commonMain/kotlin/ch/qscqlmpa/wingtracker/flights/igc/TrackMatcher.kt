package ch.qscqlmpa.wingtracker.flights.igc

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.flights.ExternalReference
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.syride.SYRIDE_SYSTEM
import ch.qscqlmpa.wingtracker.flights.syride.syrideFlightId
import kotlinx.datetime.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Why a track is taken to be the record of a flight already in the logbook. */
enum class MatchReason {
    // The file is named after the flight's Syride id, as Syride names the tracks it lets pilots download.
    SyrideId,

    // The track starts in the minute Syride gives as the flight's start, which is the logger's first fix.
    TakeOffTime,
}

/** A flight already in the logbook that a track may belong to; the pilot confirms before anything is linked. */
sealed interface TrackMatch {
    val flight: Flight

    data class Exact(
        override val flight: Flight,
        val reason: MatchReason,
    ) : TrackMatch

    // Same day and a similar duration: likely, but a pilot often makes several similar flights on one day.
    data class Possible(
        override val flight: Flight,
    ) : TrackMatch
}

/** What the matcher needs to know about a track. [duration] is null when no flight was detected in it. */
data class TrackFacts(
    val fileName: String,
    val firstFix: Instant,
    val duration: Duration?,
)

// Detected and logged durations of the same flight differ by how each source trims the ground time at both ends.
private val DURATION_TOLERANCE = 2.minutes
private val SYRIDE_FILE_NAME = Regex("""(\d+)\.igc""", RegexOption.IGNORE_CASE)

/** Finds the flight a track belongs to, trying the most reliable rule first (ADR 0026). */
class TrackMatcher(
    private val clock: AppClock,
) {
    fun match(
        track: TrackFacts,
        flights: List<Flight>,
    ): TrackMatch? =
        bySyrideId(track, flights)?.let { TrackMatch.Exact(it, MatchReason.SyrideId) }
            ?: byTakeOffTime(track, flights)?.let { TrackMatch.Exact(it, MatchReason.TakeOffTime) }
            ?: byDuration(track, flights)?.let { TrackMatch.Possible(it) }

    private fun bySyrideId(
        track: TrackFacts,
        flights: List<Flight>,
    ): Flight? {
        // A track read from a device keeps its folder in front of the file's own name.
        val name = track.fileName.substringAfterLast('/')
        val number = SYRIDE_FILE_NAME.matchEntire(name)?.groupValues?.get(1) ?: return null
        val reference = ExternalReference(SYRIDE_SYSTEM, syrideFlightId(number))
        // A logger names a track after its UTC time, which can equal the number of an old Syride flight; a download
        // always shares the flight's day, such a coincidence almost never does.
        val day = clock.local(track.firstFix).date
        return flights.firstOrNull { it.importedFrom == reference && it.details.date == day }
    }

    private fun byTakeOffTime(
        track: TrackFacts,
        flights: List<Flight>,
    ): Flight? {
        // Syride drops the seconds of the start time, so the first fix is compared by its minute.
        val start = clock.local(track.firstFix)
        val minute = LocalTime(start.hour, start.minute)
        return flights.firstOrNull { it.details.date == start.date && it.details.takeOffTime == minute }
    }

    private fun byDuration(
        track: TrackFacts,
        flights: List<Flight>,
    ): Flight? {
        val duration = track.duration ?: return null
        val day = clock.local(track.firstFix).date
        // A flight that has a track already is accounted for, so it is no candidate for a guess.
        return flights
            .filter { it.details.date == day && it.igcFile == null }
            .map { it to (it.details.duration - duration).absoluteValue }
            .filter { (_, difference) -> difference <= DURATION_TOLERANCE }
            .minByOrNull { (_, difference) -> difference }
            ?.first
    }
}
