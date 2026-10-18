package ch.qscqlmpa.wingtracker.flights.igc

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.maps.GeoPoint
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// Climb and sink are averaged over this much time, like a vario does, so one noisy altitude cannot make a record.
private val VERTICAL_SPEED_WINDOW = 20.seconds
private const val MIN_VERTICAL_MEASURE_SECONDS = 10.0
private const val MILLIS_PER_SECOND = 1000.0

/** What a flight's track shows, worked out again whenever it is looked at, so nothing derived is ever stored. */
data class AnalysedTrack(
    // The whole recording, also what the logger kept before take-off and after landing.
    val fixes: List<TrackFix>,
    // Null when the track never really moves; it is then still shown, but has no statistics.
    val flight: DetectedFlight?,
    val statistics: TrackStatistics?,
    // Along the flight, in chronological order; empty when the logger recorded no altitudes.
    val climbRates: List<ClimbRate> = emptyList(),
) {
    /** The fixes from take-off to landing. */
    val flown: List<TrackFix> get() = flight?.let { f -> fixes.filter { it.time in f.takeOff..f.landing } }.orEmpty()
}

/** The average vertical speed over the window that starts at [from]; negative while sinking. */
data class ClimbRate(
    val from: Instant,
    val metersPerSecond: Double,
)

/** Times are the pilot's local ones, like in the logbook; altitudes are null when the logger recorded none. */
data class TrackStatistics(
    val takeOffTime: LocalTime,
    val landingTime: LocalTime,
    // Where the flight started and ended, read from the track each time and never stored (ADR 0030).
    val takeOffPosition: GeoPoint,
    val landingPosition: GeoPoint,
    val takeOffAltitudeMeters: Int?,
    val landingAltitudeMeters: Int?,
    val maxAltitudeMeters: Int?,
    val bestClimbMetersPerSecond: Double?,
    // Negative, like a vario shows it.
    val maxSinkMetersPerSecond: Double?,
)

class ReadFlightTrack(
    private val attachments: AttachmentStore,
    private val reader: TrackReader,
    private val clock: AppClock,
) {
    /**
     * Null for a flight without a track.
     * @throws InvalidTrackException when the stored file is no longer a readable track.
     */
    suspend operator fun invoke(flight: Flight): AnalysedTrack? {
        val igc = flight.igcFile ?: return null
        val fixes = reader.read(attachments.read(igc.attachment.id)).track.fixes
        val detected = detectFlight(fixes)
        val analysed = AnalysedTrack(fixes, detected, statistics = null)
        val climbRates = climbRates(analysed.flown)
        return analysed.copy(
            statistics = detected?.let { statisticsOf(it, analysed.flown, climbRates) },
            climbRates = climbRates,
        )
    }

    private fun statisticsOf(
        detected: DetectedFlight,
        flown: List<TrackFix>,
        climbRates: List<ClimbRate>,
    ): TrackStatistics {
        val speeds = climbRates.map { it.metersPerSecond }
        return TrackStatistics(
            takeOffTime = clock.local(detected.takeOff).time.inWholeSeconds(),
            landingTime = clock.local(detected.landing).time.inWholeSeconds(),
            takeOffPosition = flown.first().position(),
            landingPosition = flown.last().position(),
            takeOffAltitudeMeters = flown.firstNotNullOfOrNull { it.altitudeMeters },
            landingAltitudeMeters = flown.lastOrNull { it.altitudeMeters != null }?.altitudeMeters,
            maxAltitudeMeters = flown.mapNotNull { it.altitudeMeters }.maxOrNull(),
            bestClimbMetersPerSecond = speeds.maxOrNull()?.takeIf { it > 0 },
            maxSinkMetersPerSecond = speeds.minOrNull()?.takeIf { it < 0 },
        )
    }
}

/** For each fix with an altitude, the average vertical speed to the first fix at least the window later. */
private fun climbRates(fixes: List<TrackFix>): List<ClimbRate> {
    val withAltitude = fixes.filter { it.altitudeMeters != null }
    val speeds = mutableListOf<ClimbRate>()
    var k = 0
    for (i in withAltitude.indices) {
        if (k < i) k = i
        while (k < withAltitude.lastIndex && withAltitude[k].time - withAltitude[i].time < VERTICAL_SPEED_WINDOW) k++
        val seconds = (withAltitude[k].time - withAltitude[i].time).inWholeMilliseconds / MILLIS_PER_SECOND
        if (seconds < MIN_VERTICAL_MEASURE_SECONDS) continue
        val climbed = checkNotNull(withAltitude[k].altitudeMeters) - checkNotNull(withAltitude[i].altitudeMeters)
        speeds += ClimbRate(withAltitude[i].time, climbed / seconds)
    }
    return speeds
}

private fun TrackFix.position() = GeoPoint(latitude, longitude)

private fun LocalTime.inWholeSeconds() = LocalTime(hour, minute, second)
