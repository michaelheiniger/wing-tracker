package ch.qscqlmpa.wingtracker.flights.igc

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class TrackFix(
    val time: Instant,
    val latitude: Double,
    val longitude: Double,
    // Null when the logger had no three-dimensional position fix.
    val gpsAltitudeMeters: Int? = null,
    // Null when the logger has no barometer.
    val pressureAltitudeMeters: Int? = null,
) {
    /**
     * GPS altitude is close to the height above sea level, as flight viewers show it; the pressure altitude refers to
     * the standard atmosphere and so depends on the weather. It only stands in when the logger had no GPS altitude.
     */
    val altitudeMeters: Int? get() = gpsAltitudeMeters ?: pressureAltitudeMeters
}

/** [date] is the UTC date of the first fix, as written in the file header; [fixes] are in chronological order. */
data class FlightTrack(
    val date: LocalDate,
    val fixes: List<TrackFix>,
)

data class ReadTrack(
    val track: FlightTrack,
    // Identifies the exact content of the file, to recognise it when it is imported again.
    val checksum: String,
)

class InvalidTrackException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** Port to whatever library reads the file format, so the rest of the application never depends on it (ADR 0018). */
interface TrackReader {
    /** @throws InvalidTrackException when the bytes are not a readable track. */
    fun read(bytes: ByteArray): ReadTrack
}
