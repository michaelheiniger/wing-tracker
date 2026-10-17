package ch.qscqlmpa.wingtracker.maptiles

import ch.qscqlmpa.wingtracker.maps.MapTileSource
import ch.qscqlmpa.wingtracker.maps.TileCoordinates
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.io.path.writeBytes
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

private const val MAX_ZOOM = 17
private const val HTTP_OK = 200

// The provider's servers; requests are spread over them as the provider asks.
private const val URL_PATTERN = "https://%s.tile.opentopomap.org/%d/%d/%d.png"
private val SERVERS = listOf("a", "b", "c")

// Terrain hardly changes, and the provider asks clients to fetch as little as possible.
private val MAX_AGE = 30.days

// Enough for the surroundings of many flights at every zoom level.
private const val CACHE_LIMIT_BYTES = 200L * 1024 * 1024

private val logger = KotlinLogging.logger {}

/**
 * Serves OpenTopoMap tiles, keeping each fetched tile in [directory], a cache folder of this installation (ADR 0027).
 * An outdated tile is fetched again, and still served when that fails, so a flight viewed before can be seen offline.
 */
class OpenTopoMapTileSource(
    private val directory: Path,
    private val userAgent: String,
    private val get: suspend (url: String, userAgent: String) -> TileAnswer = ::httpGetTile,
    private val now: () -> Instant = { Clock.System.now() },
    private val maxAge: Duration = MAX_AGE,
    private val cacheLimitBytes: Long = CACHE_LIMIT_BYTES,
) : MapTileSource {
    override val maxZoom: Int = MAX_ZOOM

    private val trimmed = AtomicBoolean(false)

    // A map asks for dozens of tiles at once; one line in the log says enough about being offline.
    private val reportedFailure = AtomicBoolean(false)

    override suspend fun tile(coordinates: TileCoordinates): ByteArray? =
        withContext(Dispatchers.IO) {
            if (!trimmed.getAndSet(true)) trimCache()
            val file = directory.resolve("${coordinates.zoom}/${coordinates.x}/${coordinates.y}.png")
            val cached = file.takeIf { it.isRegularFile() }
            if (cached != null && now() - cached.getLastModifiedTime().toInstant().toKotlinInstant() < maxAge) {
                return@withContext cached.readBytes()
            }
            fetch(coordinates)?.also { store(file, it) } ?: cached?.readBytes()
        }

    private suspend fun fetch(coordinates: TileCoordinates): ByteArray? {
        val (zoom, x, y) = coordinates
        val url = URL_PATTERN.format(SERVERS[(x + y) % SERVERS.size], zoom, x, y)
        return try {
            val answer = get(url, userAgent)
            if (answer.status == HTTP_OK) {
                answer.body
            } else {
                logger.warn { "The tile server answered ${answer.status} for $url" }
                null
            }
        } catch (e: IOException) {
            if (!reportedFailure.getAndSet(true)) logger.info { "Map tiles cannot be fetched: ${e.message}" }
            null
        }
    }

    // Written under a temporary name, so a tile cut off by a full disk or a crash is never served.
    private fun store(
        file: Path,
        bytes: ByteArray,
    ) {
        try {
            file.parent.createDirectories()
            val partial = file.resolveSibling("${file.fileName}.part")
            partial.writeBytes(bytes)
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            // The tile is still shown; it is only fetched again next time.
            logger.warn(e) { "Could not cache the map tile $file" }
        }
    }

    /** Deletes the oldest tiles once the cache is larger than its limit. */
    private fun trimCache() {
        if (!Files.isDirectory(directory)) return
        try {
            val tiles =
                directory
                    .walk()
                    .filter { it.isRegularFile() }
                    .map { CachedTile(it, it.fileSize(), it.getLastModifiedTime().toMillis()) }
                    .toList()
            var total = tiles.sumOf { it.size }
            if (total <= cacheLimitBytes) return
            for (tile in tiles.sortedBy { it.modifiedMillis }) {
                if (total <= cacheLimitBytes) break
                tile.path.deleteIfExists()
                total -= tile.size
            }
            logger.info { "Trimmed the map tile cache in $directory" }
        } catch (e: IOException) {
            logger.warn(e) { "Could not trim the map tile cache in $directory" }
        }
    }

    private class CachedTile(
        val path: Path,
        val size: Long,
        val modifiedMillis: Long,
    )
}
