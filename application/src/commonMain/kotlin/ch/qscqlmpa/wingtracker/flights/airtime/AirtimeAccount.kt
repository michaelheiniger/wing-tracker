package ch.qscqlmpa.wingtracker.flights.airtime

import ch.qscqlmpa.wingtracker.flights.Flight
import kotlin.time.Instant

/** The name air-time carries in [ch.qscqlmpa.wingtracker.flights.FlightExport.system]. */
const val AIRTIME_SYSTEM = "airtime"

/** A glider of the pilot on air-time; [size] is empty when none was given there. */
data class AirtimeGlider(
    val id: String,
    val brand: String,
    val model: String,
    val size: String,
)

/** A glider to create on air-time. air-time lets the client choose the [id], and requires every field. */
data class NewAirtimeGlider(
    val id: String,
    val name: String,
    val brand: String,
    val model: String,
    val size: String,
    val colour: String,
)

/** A flight of the pilot on air-time. */
data class AirtimeFlight(
    val id: Long,
    val start: Instant,
)

data class AirtimeTakeOff(
    val id: Long,
    val name: String,
)

/** A flight without a track. The adapter adds air-time's default flight type. */
data class ManualAirtimeFlight(
    val start: Instant,
    val end: Instant,
    val gliderId: String,
    val takeOffId: Long?,
)

enum class AirtimeProblem {
    // No connection could be made: most likely this computer is offline.
    NoConnection,
    Unreachable,
    TokenRefused,
    PilotNotFound,

    // air-time answered something the adapter cannot read; its API is not documented as stable (ADR 0029).
    UnexpectedAnswer,
}

class AirtimeException(
    val problem: AirtimeProblem,
    detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail ?: problem.name, cause)

/**
 * The pilot's account on air-time, reached through the API its web app uses (ADR 0029). Calls taking a [token]
 * act as the pilot; the others read public data.
 *
 * Every call throws [AirtimeException] when air-time cannot be reached, refuses the token or answers unexpectedly.
 */
interface AirtimeAccount {
    suspend fun gliders(token: String): List<AirtimeGlider>

    suspend fun createGlider(
        token: String,
        glider: NewAirtimeGlider,
    )

    /** When each flight the pilot has on air-time started. */
    suspend fun flightStarts(nick: String): List<Instant>

    /** The pilot's flights starting from [from] to [to], both included. */
    suspend fun flightsStartingBetween(
        nick: String,
        from: Instant,
        to: Instant,
    ): List<AirtimeFlight>

    suspend fun takeOffs(): List<AirtimeTakeOff>

    suspend fun uploadTrack(
        token: String,
        igc: ByteArray,
        fileName: String,
        gliderId: String,
    )

    suspend fun registerManualFlight(
        token: String,
        flight: ManualAirtimeFlight,
    )

    /** The page where the pilot generates a token, to open in the browser. */
    fun tokenPage(): String

    /** The pilot's public page, to look at the flights just sent. */
    fun pilotPage(nick: String): String

    /** The page of one flight, by its id on air-time. */
    fun flightPage(id: Long): String
}

/** The air-time pages the pilot is sent to; their addresses are the adapter's business. */
class GetAirtimePages(
    private val account: AirtimeAccount,
) {
    fun tokenPage(): String = account.tokenPage()

    fun pilotPage(nick: String): String = account.pilotPage(nick.trim())

    /** The flight's page on air-time; null when it was not exported there or its id is not known. */
    fun flightPage(flight: Flight): String? =
        flight
            .exportTo(AIRTIME_SYSTEM)
            ?.externalId
            ?.toLongOrNull()
            ?.let { account.flightPage(it) }
}
