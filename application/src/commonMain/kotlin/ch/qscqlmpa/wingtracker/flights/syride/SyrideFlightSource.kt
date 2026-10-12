package ch.qscqlmpa.wingtracker.flights.syride

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Duration

/** The name Syride flights carry in [ch.qscqlmpa.wingtracker.flights.ExternalReference.system]. */
const val SYRIDE_SYSTEM = "syride"

/** The wing a flight was flown with, as named on Syride; the pairing of name and brand identifies it. */
data class SyrideWing(
    val name: String,
    val brand: String?,
)

/** One flight of a Syride pilot. Flights logged without an instrument have no track ([hasTrack] is false). */
data class SyrideFlight(
    // Stable on Syride, so a flight is recognised when the import is run again.
    val id: String,
    val hasTrack: Boolean,
    val name: String?,
    val date: LocalDate,
    val startTime: LocalTime?,
    val duration: Duration,
    val siteName: String?,
    val wing: SyrideWing,
    val distanceKm: Double?,
    val topAltitudeMeters: Int?,
    val instrument: String?,
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
