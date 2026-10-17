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
}
