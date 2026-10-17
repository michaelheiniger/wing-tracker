package ch.qscqlmpa.wingtracker.github

import ch.qscqlmpa.wingtracker.updates.AppVersion
import ch.qscqlmpa.wingtracker.updates.Release
import ch.qscqlmpa.wingtracker.updates.ReleaseAsset
import ch.qscqlmpa.wingtracker.updates.ReleaseProblem
import ch.qscqlmpa.wingtracker.updates.ReleaseSource
import ch.qscqlmpa.wingtracker.updates.ReleaseSourceException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val API = "https://api.github.com"

// The most the API returns at once; far more releases than an installation falls behind by.
private const val PAGE_SIZE = 100

private val logger = KotlinLogging.logger {}

// The answer holds far more than is read here, and GitHub adds fields over time.
private val json = Json { ignoreUnknownKeys = true }

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
        checkAnswered(answer.status, "Listing the releases of $repository")
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
