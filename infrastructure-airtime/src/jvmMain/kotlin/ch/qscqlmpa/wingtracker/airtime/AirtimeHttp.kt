package ch.qscqlmpa.wingtracker.airtime

import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeException
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeProblem
import ch.qscqlmpa.wingtracker.http.ConnectionProblem
import ch.qscqlmpa.wingtracker.http.connectionProblemOf
import ch.qscqlmpa.wingtracker.http.sendAborting
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val TIMEOUT_SECONDS = 20L

// Uploading a long track over a slow connection takes longer than waiting for a short answer.
private const val UPLOAD_TIMEOUT_SECONDS = 120L

private val logger = KotlinLogging.logger {}

/** What is sent with a request. */
sealed interface AirtimeBody {
    class Json(
        val text: String,
    ) : AirtimeBody

    class Multipart(
        val bytes: ByteArray,
        val boundary: String,
    ) : AirtimeBody
}

/** One call to air-time's API; [token] is null for public data. */
class AirtimeRequest(
    val method: String,
    val url: String,
    val token: String? = null,
    val body: AirtimeBody? = null,
)

class AirtimeAnswer(
    val status: Int,
    val body: String,
)

private val client: HttpClient by lazy {
    HttpClient
        .newBuilder()
        .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .build()
}

/** Sends [request] with the JDK client, naming the application as [userAgent]. */
internal suspend fun airtimeExchange(
    request: AirtimeRequest,
    userAgent: String,
): AirtimeAnswer {
    // The token travels with the request, so it must never go over a connection that can be read.
    require(request.url.startsWith("https://")) { "Only HTTPS is used, not ${request.url}" }
    val body = request.body
    val builder =
        HttpRequest
            .newBuilder(URI.create(request.url))
            .timeout(Duration.ofSeconds(if (body is AirtimeBody.Multipart) UPLOAD_TIMEOUT_SECONDS else TIMEOUT_SECONDS))
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
    // air-time's own scheme name, as its settings page tells pilots to send the token.
    request.token?.let { builder.header("Authorization", "AIRTIME $it") }
    when (body) {
        null -> {
            builder.method(request.method, HttpRequest.BodyPublishers.noBody())
        }

        is AirtimeBody.Json -> {
            builder
                .header("Content-Type", "application/json")
                .method(request.method, HttpRequest.BodyPublishers.ofString(body.text))
        }

        is AirtimeBody.Multipart -> {
            builder
                .header("Content-Type", "multipart/form-data; boundary=${body.boundary}")
                .method(request.method, HttpRequest.BodyPublishers.ofByteArray(body.bytes))
        }
    }
    return try {
        logger.debug { "${request.method} ${request.url}" }
        val response = client.sendAborting(builder.build(), HttpResponse.BodyHandlers.ofString())
        AirtimeAnswer(response.statusCode(), response.body())
    } catch (e: IOException) {
        logger.warn(e) { "air-time could not be reached at ${request.url}" }
        val problem =
            when (connectionProblemOf(e)) {
                ConnectionProblem.NoConnection -> AirtimeProblem.NoConnection
                ConnectionProblem.Broken -> AirtimeProblem.Unreachable
            }
        throw AirtimeException(problem, e.message, e)
    }
}
