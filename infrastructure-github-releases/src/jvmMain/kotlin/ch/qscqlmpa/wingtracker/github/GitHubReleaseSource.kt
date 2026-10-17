package ch.qscqlmpa.wingtracker.github

import ch.qscqlmpa.wingtracker.updates.AppVersion
import ch.qscqlmpa.wingtracker.updates.Release
import ch.qscqlmpa.wingtracker.updates.ReleaseAsset
import ch.qscqlmpa.wingtracker.updates.ReleaseProblem
import ch.qscqlmpa.wingtracker.updates.ReleaseSource
import ch.qscqlmpa.wingtracker.updates.ReleaseSourceException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val API = "https://api.github.com"

// The most the API returns at once; far more releases than an installation falls behind by.
private const val PAGE_SIZE = 100
private const val TIMEOUT_SECONDS = 20L
private const val HTTP_OK = 200
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERRORS = 500

private val logger = KotlinLogging.logger {}

// The answer holds far more than is read here, and GitHub adds fields over time.
private val json = Json { ignoreUnknownKeys = true }

/** What an HTTP GET answered. */
class HttpAnswer(
    val status: Int,
    val body: String,
)

/**
 * Lists the releases of [repository] (owner/name) published on GitHub, through its REST API (ADR 0024).
 * [userAgent] names the application, as the API asks of every client.
 */
class GitHubReleaseSource(
    private val repository: String,
    private val userAgent: String,
    private val get: suspend (url: String, userAgent: String) -> HttpAnswer = ::httpGet,
) : ReleaseSource {
    override suspend fun published(): List<Release> {
        val answer = get("$API/repos/$repository/releases?per_page=$PAGE_SIZE", userAgent)
        when {
            answer.status == HTTP_OK -> Unit
            answer.status == HTTP_FORBIDDEN || answer.status == HTTP_TOO_MANY_REQUESTS -> refused(answer.status)
            answer.status >= HTTP_SERVER_ERRORS -> fail(ReleaseProblem.Unreachable, "GitHub answered ${answer.status}")
            else -> fail(ReleaseProblem.Unexpected, "GitHub answered ${answer.status}")
        }
        val releases =
            try {
                json.decodeFromString<List<ReleaseJson>>(answer.body)
            } catch (e: SerializationException) {
                logger.warn(e) { "The release list of $repository could not be read" }
                throw ReleaseSourceException(ReleaseProblem.Unexpected, e.message, e)
            }
        return releases
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { it.toDomain() }
            .also { logger.info { "GitHub lists ${it.size} releases of $repository" } }
    }

    // Unauthenticated clients get a small hourly allowance per network; it is enough unless many share an address.
    private fun refused(status: Int): Nothing = fail(ReleaseProblem.Refused, "GitHub answered $status")

    private fun fail(
        problem: ReleaseProblem,
        detail: String,
    ): Nothing {
        logger.warn { "Listing the releases of $repository failed: $detail" }
        throw ReleaseSourceException(problem, detail)
    }
}

@Serializable
private class ReleaseJson(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<AssetJson> = emptyList(),
) {
    /** Null for a tag that is no release version (ADR 0009), which the application cannot compare. */
    fun toDomain(): Release? {
        val version = AppVersion.parseOrNull(tagName)
        if (version == null) {
            logger.info { "Ignoring release $tagName, whose tag is not a version" }
            return null
        }
        return Release(version, body.orEmpty(), htmlUrl, assets.map { ReleaseAsset(it.name, it.downloadUrl) })
    }
}

@Serializable
private class AssetJson(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)

private val client: HttpClient by lazy {
    HttpClient
        .newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .build()
}

private suspend fun httpGet(
    url: String,
    userAgent: String,
): HttpAnswer =
    withContext(Dispatchers.IO) {
        val request =
            HttpRequest
                .newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("User-Agent", userAgent)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build()
        try {
            logger.debug { "GET $url" }
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            HttpAnswer(response.statusCode(), response.body())
        } catch (e: IOException) {
            logger.warn(e) { "GitHub could not be reached at $url" }
            throw ReleaseSourceException(ReleaseProblem.Unreachable, e.message, e)
        }
    }
