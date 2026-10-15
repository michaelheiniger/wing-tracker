package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.ExternalReference
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.importing.ImportableFlight
import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Duration

/** The name Syride flights carry in [ch.qscqlmpa.wingtracker.flights.ExternalReference.system]. */
const val SYRIDE_SYSTEM = "syride"

/** One flight of a Syride pilot. Flights logged without an instrument have no track ([hasTrack] is false). */
data class SyrideFlight(
    // Stable on Syride, so a flight is recognised when the import is run again.
    val id: String,
    val hasTrack: Boolean,
    val name: String?,
    override val date: LocalDate,
    val startTime: LocalTime?,
    override val duration: Duration,
    val siteName: String?,
    override val wing: SourceWing,
    val distanceKm: Double?,
    val topAltitudeMeters: Int?,
    val instrument: String?,
    // Null when the earlier reader would have read the flight the same way, or could not tell.
    val earlierReading: EarlierReading? = null,
) : ImportableFlight {
    override val externalReference: ExternalReference get() = ExternalReference(SYRIDE_SYSTEM, id)

    override fun toDetails(wingId: WingId) =
        FlightDetails(
            date = date,
            duration = duration,
            wingId = wingId,
            takeOffSite = siteName,
            notes = name,
            distanceKm = distanceKm,
            topAltitudeMeters = topAltitudeMeters,
            instrument = instrument,
        )
}

/**
 * How the reader used until 2026-10-16 read a flight's name and site: it cut them at the first parenthesis and left
 * most accents as HTML entities. Flights it imported still carry those texts; knowing them tells such a text from one
 * the pilot wrote, so only the former is corrected.
 */
data class EarlierReading(
    val name: String?,
    val siteName: String?,
)

enum class SyrideProblem {
    UserNotFound,
    Unreachable,

    // The pages no longer look the way the reader expects; this is the fragile part of the integration (ADR 0021).
    PagesChanged,
}

class SyrideException(
    val problem: SyrideProblem,
    detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail ?: problem.name, cause)

/** Reads the public flight list of a Syride pilot. There is no official API, so this is an adapter (ADR 0021). */
interface SyrideFlightSource {
    /** @throws SyrideException when the pilot is unknown, Syride cannot be reached or its pages changed. */
    suspend fun fetch(username: String): List<SyrideFlight>
}
