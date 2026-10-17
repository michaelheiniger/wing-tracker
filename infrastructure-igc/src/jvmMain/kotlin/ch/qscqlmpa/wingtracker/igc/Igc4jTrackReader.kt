package ch.qscqlmpa.wingtracker.igc

import ch.qscqlmpa.wingtracker.flights.igc.FlightTrack
import ch.qscqlmpa.wingtracker.flights.igc.InvalidTrackException
import ch.qscqlmpa.wingtracker.flights.igc.ReadTrack
import ch.qscqlmpa.wingtracker.flights.igc.TrackFix
import ch.qscqlmpa.wingtracker.flights.igc.TrackReader
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import ru.paragliding.igc4j.model.FixValidity
import ru.paragliding.igc4j.model.IGCTrackPoint
import ru.paragliding.igc4j.parser.IGCParser
import ru.paragliding.igc4j.parser.InvalidIGCFormatException
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private const val MILLENNIUM = 2000

private val logger = KotlinLogging.logger {}

// The header writes the date as day, month and two-digit year.
private val DATE_FORMAT = Regex("""(\d{2})(\d{2})(\d{2})""")

/**
 * Reads IGC files with igc4j. The library gives the header date and each fix's time as milliseconds since the start
 * of the day; turning those into points in time, including a flight that crosses midnight UTC, is done here.
 */
class Igc4jTrackReader : TrackReader {
    override fun read(bytes: ByteArray): ReadTrack =
        try {
            readValid(bytes).also {
                logger.debug {
                    "Read a track of ${it.track.date} with ${it.track.fixes.size} fixes (${bytes.size} bytes)"
                }
            }
        } catch (e: InvalidTrackException) {
            // Expected for a wrong file, so the pilot gets a message; logged for the case where the reader is wrong.
            logger.info { "Refused a track of ${bytes.size} bytes: ${e.message}" }
            throw e
        }

    private fun readValid(bytes: ByteArray): ReadTrack {
        val parsed =
            try {
                IGCParser.parse(ByteArrayInputStream(bytes))
            } catch (e: InvalidIGCFormatException) {
                throw InvalidTrackException("The file is not a valid IGC track: ${e.message}", e)
            }
        val date = parseDate(parsed.metadata?.date)
        if (parsed.points.isEmpty()) throw InvalidTrackException("The file contains no position fixes")
        return ReadTrack(
            FlightTrack(
                date,
                fixesOf(date, parsed.points),
            ),
            sha256(bytes),
        )
    }

    private fun fixesOf(
        startDate: LocalDate,
        points: List<IGCTrackPoint>,
    ): List<TrackFix> {
        var dayOffset = 0
        var previous = -1L
        return points.map { point ->
            val millisOfDay = point.timestamp
            // Times only count up within a day; a smaller time than before means midnight UTC has passed.
            if (millisOfDay < previous) dayOffset++
            previous = millisOfDay
            val day = startDate.plus(dayOffset, DateTimeUnit.DAY)
            TrackFix(
                day.atStartOfDayIn(TimeZone.UTC) + millisOfDay.milliseconds,
                point.lat,
                point.lon,
                // Loggers write zero for an altitude they do not have; a paraglider is never really at sea level.
                gpsAltitudeMeters = point.gpsAltitude.takeIf { point.validity == FixValidity.A && it != 0 },
                pressureAltitudeMeters = point.barometricAltitude.takeIf { it != 0 },
            )
        }
    }

    private fun parseDate(text: String?): LocalDate {
        val match =
            text?.let { DATE_FORMAT.matchEntire(it) }
                ?: throw InvalidTrackException("The file has no valid flight date")
        val (day, month, year) = match.destructured
        return try {
            LocalDate(MILLENNIUM + year.toInt(), month.toInt(), day.toInt())
        } catch (e: IllegalArgumentException) {
            throw InvalidTrackException("The flight date '$text' is not a valid date", e)
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it)
        }
}
