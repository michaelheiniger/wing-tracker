package ch.qscqlmpa.wingtracker.github

import ch.qscqlmpa.wingtracker.updates.ReleaseProblem
import ch.qscqlmpa.wingtracker.updates.ReleaseSourceException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val HTTP_OK = 200
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERRORS = 500
private const val TIMEOUT_SECONDS = 20L

private val logger = KotlinLogging.logger {}

/** What an HTTP GET answered. */
class HttpAnswer(
    val status: Int,
    val body: String,
)

/** The start of an HTTP answer whose body is read as it arrives; [length] is null when the server does not say. */
class HttpStream(
    val status: Int,
    val length: Long?,
    val body: InputStream,
)

private val client: HttpClient by lazy {
    HttpClient
        .newBuilder()
        // Release files are served from another host than the one listing them.
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .build()
}

private fun request(
    url: String,
    userAgent: String,
): HttpRequest {
    // Nothing that is installed may arrive over a connection that can be tampered with.
    require(url.startsWith("https://")) { "Only HTTPS is used, not $url" }
    return HttpRequest
        .newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .header("User-Agent", userAgent)
        .header("Accept", "application/vnd.github+json")
        .GET()
        .build()
}

internal suspend fun httpGet(
    url: String,
    userAgent: String,
): HttpAnswer =
    send(url) {
        val response = client.send(request(url, userAgent), HttpResponse.BodyHandlers.ofString())
        HttpAnswer(response.statusCode(), response.body())
    }

internal suspend fun httpOpen(
    url: String,
    userAgent: String,
): HttpStream =
    send(url) {
        val response = client.send(request(url, userAgent), HttpResponse.BodyHandlers.ofInputStream())
        val length = response.headers().firstValueAsLong("Content-Length")
        HttpStream(response.statusCode(), if (length.isPresent) length.asLong else null, response.body())
    }

private suspend fun <T> send(
    url: String,
    block: () -> T,
): T =
    withContext(Dispatchers.IO) {
        try {
            logger.debug { "GET $url" }
            block()
        } catch (e: IOException) {
            logger.warn(e) { "GitHub could not be reached at $url" }
            throw ReleaseSourceException(problemOf(e), e.message, e)
        }
    }

/** Tells a connection that could not be made, most likely because this computer is offline, from one that broke. */
internal fun problemOf(e: IOException): ReleaseProblem =
    when (e) {
        // The JDK reports an unknown host and a missing network alike, as a connection that could not be made.
        is ConnectException, is UnknownHostException, is HttpConnectTimeoutException -> ReleaseProblem.NoConnection

        else -> ReleaseProblem.Unreachable
    }

/** Throws what a status other than OK means for the pilot; [what] says what was being done, for the log. */
internal fun checkAnswered(
    status: Int,
    what: String,
) {
    val problem =
        when {
            status == HTTP_OK -> return

            // Unauthenticated clients get a small hourly allowance per network; enough unless many share an address.
            status == HTTP_FORBIDDEN || status == HTTP_TOO_MANY_REQUESTS -> ReleaseProblem.Refused

            status >= HTTP_SERVER_ERRORS -> ReleaseProblem.Unreachable

            else -> ReleaseProblem.Unexpected
        }
    logger.warn { "$what failed: GitHub answered $status" }
    throw ReleaseSourceException(problem, "GitHub answered $status")
}
