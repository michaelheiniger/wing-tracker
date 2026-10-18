package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.ExternalReference
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.SiteNames
import ch.qscqlmpa.wingtracker.flights.SiteSource
import ch.qscqlmpa.wingtracker.flights.importing.ImportableFlight
import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Duration

/** The name Syride flights carry in [ch.qscqlmpa.wingtracker.flights.ExternalReference.system]. */
const val SYRIDE_SYSTEM = "syride"

/**
 * The id of a Syride flight with a track. Flights and activities are numbered separately on Syride, so the kind is part
 * of the id; tracks downloaded from Syride are named after the flight number (ADR 0026).
 */
fun syrideFlightId(flightNumber: String): String = "$FLIGHT_PREFIX$flightNumber"

private const val FLIGHT_PREFIX = "flight:"

/** Whether a flight came from Syride with a track there, which can then be imported and attached to it. */
fun ExternalReference.isSyrideFlightWithTrack(): Boolean = system == SYRIDE_SYSTEM && id.startsWith(FLIGHT_PREFIX)

/** Syride's number of a flight with a track; null for anything else, which has no flight page there. */
fun ExternalReference.syrideFlightNumber(): String? =
    id.removePrefix(FLIGHT_PREFIX).takeIf {
        isSyrideFlightWithTrack()
    }

/**
 * The id of a Syride flight without a track. Syride calls such an entry an activity and only gives it an activity
 * number; the prefix uses the app's own words, so stored data does not depend on Syride's vocabulary.
 */
fun syrideUntrackedFlightId(activityNumber: Int): String = "$UNTRACKED_FLIGHT_PREFIX$activityNumber"

internal const val UNTRACKED_FLIGHT_PREFIX = "untracked-flight:"

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
) : ImportableFlight {
    override val externalReference: ExternalReference get() = ExternalReference(SYRIDE_SYSTEM, id)

    override fun toDetails(wingId: WingId) =
        FlightDetails(
            date = date,
            duration = duration,
            wingId = wingId,
            takeOffSite = SiteNames.of(SiteSource.Syride, siteName),
            notes = name,
            takeOffTime = startTime,
            distanceKm = distanceKm,
            topAltitudeMeters = topAltitudeMeters,
            instrument = instrument,
        )
}

enum class SyrideProblem {
    UserNotFound,

    // No connection could be made: most likely this computer is offline.
    NoConnection,
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

    /** The address of a flight's page on Syride, to open in the browser. */
    fun flightPage(
        username: String,
        flightNumber: String,
    ): String
}
