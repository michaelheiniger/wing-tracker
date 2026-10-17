package ch.qscqlmpa.wingtracker.airtime

import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeAccount
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeException
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeGlider
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeProblem
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeTakeOff
import ch.qscqlmpa.wingtracker.flights.airtime.ManualAirtimeFlight
import ch.qscqlmpa.wingtracker.flights.airtime.NewAirtimeGlider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.UUID
import kotlin.time.Instant

private const val HTTP_OK = 200
private const val HTTP_NO_CONTENT = 204
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_SERVER_ERRORS = 500

// The most flights air-time returns at once.
private const val PAGE_SIZE = 200

// air-time's default flight type, which its own form preselects (ADR 0029).
private const val SITE_FLIGHT = "site flight"

private val logger = KotlinLogging.logger {}

// The answers hold far more than is read here, and air-time adds fields over time.
private val json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

/**
 * The pilot's account on air-time at [baseUrl], through the API its web app uses (ADR 0029). [userAgent] names the
 * application, so air-time can tell its requests apart.
 */
class AirtimeWebAccount(
    private val baseUrl: String,
    private val userAgent: String,
    private val exchange: suspend (AirtimeRequest, String) -> AirtimeAnswer = ::airtimeExchange,
) : AirtimeAccount {
    override suspend fun gliders(token: String): List<AirtimeGlider> {
        val answer = call(AirtimeRequest("GET", "$baseUrl/v0/gliders", token), "Listing the gliders")
        return read<GlidersJson>(answer, "The glider list").gliders.map {
            AirtimeGlider(it.id, it.brand, it.model, it.size)
        }
    }

    override suspend fun createGlider(
        token: String,
        glider: NewAirtimeGlider,
    ) {
        val body =
            GliderJson(glider.id, glider.name, glider.brand, glider.model, glider.size, glider.colour, retired = false)
        call(
            AirtimeRequest("POST", "$baseUrl/v0/gliders", token, AirtimeBody.Json(json.encodeToString(body))),
            "Creating the glider ${glider.name}",
        )
        logger.info { "Created the glider ${glider.name} on air-time" }
    }

    override suspend fun flightStarts(nick: String): List<Instant> {
        val starts = mutableListOf<Instant>()
        val path = pathSegment(nick)
        do {
            val url = "$baseUrl/v0/pilots/$path/flights?limit=$PAGE_SIZE&offset=${starts.size}"
            val page =
                read<FlightPageJson>(call(AirtimeRequest("GET", url), "Listing the flights of $nick"), "A flight list")
            page.flights.mapTo(starts) { parseInstant(it.startTime) }
        } while (page.hasMore && page.flights.isNotEmpty())
        logger.info { "air-time lists ${starts.size} flights of $nick" }
        return starts
    }

    override suspend fun takeOffs(): List<AirtimeTakeOff> {
        val answer = call(AirtimeRequest("GET", "$baseUrl/v0/takeoffs"), "Listing the take-off sites")
        return read<TakeOffsJson>(answer, "The take-off list").takeoffs.map { AirtimeTakeOff(it.id, it.name) }
    }

    override suspend fun uploadTrack(
        token: String,
        igc: ByteArray,
        fileName: String,
        gliderId: String,
    ) {
        val boundary = "wing-tracker-${UUID.randomUUID()}"
        val body = multipart(boundary, igc, fileName, gliderId)
        call(
            AirtimeRequest("POST", "$baseUrl/v0/flights", token, AirtimeBody.Multipart(body, boundary)),
            "Uploading $fileName",
        )
    }

    override suspend fun registerManualFlight(
        token: String,
        flight: ManualAirtimeFlight,
    ) {
        val body =
            ManualFlightJson(
                flight.start.toString(),
                flight.end.toString(),
                flight.gliderId,
                flight.takeOffId,
                SITE_FLIGHT,
            )
        call(
            AirtimeRequest("POST", "$baseUrl/v0/flights/manual", token, AirtimeBody.Json(json.encodeToString(body))),
            "Registering the flight of ${flight.start}",
        )
    }

    // The token is generated on the settings page of air-time's web app.
    override fun tokenPage(): String = "$baseUrl/settings"

    override fun pilotPage(nick: String): String = "$baseUrl/pilot/${pathSegment(nick)}"

    private suspend fun call(
        request: AirtimeRequest,
        what: String,
    ): AirtimeAnswer {
        val answer = exchange(request, userAgent)
        val problem =
            when {
                answer.status == HTTP_OK || answer.status == HTTP_NO_CONTENT -> {
                    return answer
                }

                answer.status == HTTP_UNAUTHORIZED || answer.status == HTTP_FORBIDDEN -> {
                    AirtimeProblem.TokenRefused
                }

                // Only a pilot's flights are looked up by a name the pilot typed; anything else missing is unexpected.
                answer.status == HTTP_NOT_FOUND && request.token == null && "/pilots/" in request.url -> {
                    AirtimeProblem.PilotNotFound
                }

                answer.status >= HTTP_SERVER_ERRORS -> {
                    AirtimeProblem.Unreachable
                }

                else -> {
                    AirtimeProblem.UnexpectedAnswer
                }
            }
        // air-time explains a refusal in the body; it never contains the token.
        logger.warn { "$what failed: air-time answered ${answer.status} ${answer.body.take(LOGGED_BODY_CHARS)}" }
        throw AirtimeException(problem, "air-time answered ${answer.status}")
    }

    private inline fun <reified T> read(
        answer: AirtimeAnswer,
        what: String,
    ): T =
        try {
            json.decodeFromString<T>(answer.body)
        } catch (e: SerializationException) {
            logger.warn(e) { "$what from air-time could not be read" }
            throw AirtimeException(AirtimeProblem.UnexpectedAnswer, e.message, e)
        } catch (e: IllegalArgumentException) {
            logger.warn(e) { "$what from air-time could not be read" }
            throw AirtimeException(AirtimeProblem.UnexpectedAnswer, e.message, e)
        }

    private fun parseInstant(text: String): Instant =
        try {
            Instant.parse(text)
        } catch (e: IllegalArgumentException) {
            throw AirtimeException(AirtimeProblem.UnexpectedAnswer, "Unreadable start time '$text'", e)
        }
}

private const val LOGGED_BODY_CHARS = 500

// A nick is typed by the pilot, so it is encoded before it becomes part of an address.
private fun pathSegment(nick: String) = URLEncoder.encode(nick, Charsets.UTF_8).replace("+", "%20")

/** The form air-time's upload expects: the track as `file`, and the glider it was flown with. */
internal fun multipart(
    boundary: String,
    igc: ByteArray,
    fileName: String,
    gliderId: String,
): ByteArray {
    // The name ends up in a header line, where quotes and line breaks would end the field.
    val safeName = fileName.replace(Regex("[\"\\r\\n]"), "_")
    val out = ByteArrayOutputStream()

    fun text(value: String) = out.write(value.toByteArray(Charsets.UTF_8))
    text("--$boundary\r\n")
    text("Content-Disposition: form-data; name=\"file\"; filename=\"$safeName\"\r\n")
    text("Content-Type: application/octet-stream\r\n\r\n")
    out.write(igc)
    text("\r\n--$boundary\r\n")
    text("Content-Disposition: form-data; name=\"glider_id\"\r\n\r\n")
    text(gliderId)
    text("\r\n--$boundary--\r\n")
    return out.toByteArray()
}

@Serializable
private class GlidersJson(
    val gliders: List<GliderJson> = emptyList(),
)

@Serializable
private class GliderJson(
    val id: String,
    val name: String,
    val brand: String,
    val model: String,
    val size: String,
    val color: String,
    val retired: Boolean = false,
)

@Serializable
private class FlightPageJson(
    val flights: List<FlightSummaryJson>,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
private class FlightSummaryJson(
    @SerialName("start_time") val startTime: String,
)

@Serializable
private class TakeOffsJson(
    val takeoffs: List<TakeOffJson>,
)

@Serializable
private class TakeOffJson(
    val id: Long,
    val name: String,
)

@Serializable
private class ManualFlightJson(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("glider_id") val gliderId: String,
    @SerialName("takeoff_id") val takeOffId: Long?,
    val type: String,
)
