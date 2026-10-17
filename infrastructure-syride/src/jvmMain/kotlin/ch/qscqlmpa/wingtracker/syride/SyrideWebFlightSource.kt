package ch.qscqlmpa.wingtracker.syride

import ch.qscqlmpa.wingtracker.flights.syride.SyrideException
import ch.qscqlmpa.wingtracker.flights.syride.SyrideFlight
import ch.qscqlmpa.wingtracker.flights.syride.SyrideFlightSource
import ch.qscqlmpa.wingtracker.flights.syride.SyrideProblem
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val BASE = "https://syride.com"
private const val PAGE_SIZE = 100
private const val MAX_PAGES = 200
private const val TIMEOUT_SECONDS = 30L
private const val HTTP_OK = 200
private const val PERCENT = 100

// A small community site: one request after the other with a pause, never in parallel.
private const val PAUSE_MILLIS = 500L

// More unreadable flight cards than this means the pages changed, not that a few flights are odd.
private const val TOLERATED_UNREADABLE_PERCENT = 10

private val logger = KotlinLogging.logger {}

/** Reads a pilot's flights from Syride's public pages, the same ones the profile page loads while scrolling. */
class SyrideWebFlightSource(
    private val fetchPage: suspend (url: String, referer: String) -> String = ::httpGet,
    private val pause: suspend () -> Unit = { delay(PAUSE_MILLIS) },
) : SyrideFlightSource {
    override suspend fun fetch(username: String): List<SyrideFlight> {
        val profile = "$BASE/en/pilots/${URLEncoder.encode(username, Charsets.UTF_8)}"
        val rider = riderId(fetchPage(profile, profile))
        logger.info { "Reading the flights of Syride pilot $username (id $rider)" }
        val flights = mutableListOf<SyrideFlight>()
        var unreadable = 0
        var cards = 0
        var before = 0
        repeat(MAX_PAGES) {
            val url =
                "$BASE/ajax/pilotes/activites.php?limit=$PAGE_SIZE&l=en&idRider=$rider&apli=0" +
                    if (before > 0) "&append=1&beforeId=$before" else ""
            val page = SyrideActivityParser.parsePage(fetchPage(url, profile))
            if (page.activityIds.isEmpty()) return finish(flights, unreadable, cards)
            logger.debug { "Page with ${page.flights.size} flights and ${page.unreadableFlights} unreadable cards" }
            flights += page.flights
            unreadable += page.unreadableFlights
            cards += page.flights.size + page.unreadableFlights
            before = page.activityIds.last()
            pause()
        }
        return finish(flights, unreadable, cards)
    }

    private fun finish(
        flights: List<SyrideFlight>,
        unreadable: Int,
        cards: Int,
    ): List<SyrideFlight> {
        if (cards > 0 && unreadable * PERCENT / cards > TOLERATED_UNREADABLE_PERCENT) {
            logger.warn { "$unreadable of $cards Syride flight cards could not be read; the pages changed" }
            throw SyrideException(SyrideProblem.PagesChanged, "$unreadable of $cards flights could not be read")
        }
        if (unreadable > 0) logger.warn { "$unreadable of $cards Syride flight cards could not be read" }
        logger.info { "Read ${flights.size} flights from Syride" }
        return flights
    }

    private fun riderId(profile: String): Int {
        val id =
            Regex("idRider=(\\d+)")
                .find(profile)
                ?.groupValues
                ?.get(1)
                ?.toInt()
                ?: run {
                    logger.warn { "The Syride profile page has no pilot id; the pages changed" }
                    throw SyrideException(SyrideProblem.PagesChanged, "The profile page has no pilot id")
                }
        // Syride answers an unknown name with a normal page whose pilot id is 0.
        if (id == 0) {
            logger.info { "Syride knows no such pilot" }
            throw SyrideException(SyrideProblem.UserNotFound)
        }
        return id
    }
}

private val client: HttpClient by lazy {
    HttpClient
        .newBuilder()
        .followRedirects(
            HttpClient.Redirect.NORMAL,
        ).connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .build()
}

private suspend fun httpGet(
    url: String,
    referer: String,
): String =
    withContext(Dispatchers.IO) {
        val request =
            HttpRequest
                .newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("User-Agent", "wing-tracker (personal flight logbook)")
                .header("Referer", referer)
                .header("X-Requested-With", "XMLHttpRequest")
                .GET()
                .build()
        try {
            logger.debug { "GET $url" }
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != HTTP_OK) {
                logger.warn { "Syride answered ${response.statusCode()} to $url" }
                throw SyrideException(SyrideProblem.Unreachable, "Syride answered ${response.statusCode()}")
            }
            response.body()
        } catch (e: IOException) {
            logger.warn(e) { "Syride could not be reached at $url" }
            throw SyrideException(problemOf(e), e.message, e)
        }
    }

/** Tells a connection that could not be made, most likely because this computer is offline, from one that broke. */
internal fun problemOf(e: IOException): SyrideProblem =
    when (e) {
        // The JDK reports an unknown host and a missing network alike, as a connection that could not be made.
        is ConnectException, is UnknownHostException, is HttpConnectTimeoutException -> SyrideProblem.NoConnection

        else -> SyrideProblem.Unreachable
    }
