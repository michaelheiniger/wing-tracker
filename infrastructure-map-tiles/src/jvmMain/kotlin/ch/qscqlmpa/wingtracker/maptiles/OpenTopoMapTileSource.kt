package ch.qscqlmpa.wingtracker.maptiles

import ch.qscqlmpa.wingtracker.maps.MapTileSource
import ch.qscqlmpa.wingtracker.maps.TileCoordinates
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private const val MAX_ZOOM = 17

// The provider's servers; requests are spread over them as the provider asks.
private const val URL_PATTERN = "https://%s.tile.opentopomap.org/%d/%d/%d.png"
private val SERVERS = listOf("a", "b", "c")

// Terrain hardly changes, and the provider asks clients to fetch as little as possible.
private val MAX_AGE = 30.days

// Enough for the surroundings of many flights at every zoom level.
private const val CACHE_LIMIT_BYTES = 200L * 1024 * 1024

/** Serves OpenTopoMap tiles from [directory], fetching those it does not have (ADR 0027). */
class OpenTopoMapTileSource(
    directory: Path,
    userAgent: String,
    get: suspend (url: String, userAgent: String) -> TileAnswer = ::httpGetTile,
    now: () -> Instant = { Clock.System.now() },
    cacheLimitBytes: Long = CACHE_LIMIT_BYTES,
) : MapTileSource {
    override val maxZoom: Int = MAX_ZOOM

    private val cache =
        TileCache(directory, userAgent, ::urlOf, get, now, MAX_AGE, cacheLimitBytes)

    override suspend fun tile(coordinates: TileCoordinates): ByteArray? = cache.tile(coordinates)

    private fun urlOf(coordinates: TileCoordinates): String {
        val (zoom, x, y) = coordinates
        return URL_PATTERN.format(SERVERS[(x + y) % SERVERS.size], zoom, x, y)
    }
}
