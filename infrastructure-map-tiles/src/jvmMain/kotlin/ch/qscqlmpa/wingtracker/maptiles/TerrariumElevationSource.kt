package ch.qscqlmpa.wingtracker.maptiles

import ch.qscqlmpa.wingtracker.maps.GeoPoint
import ch.qscqlmpa.wingtracker.maps.TerrainElevationSource
import ch.qscqlmpa.wingtracker.maps.TileCoordinates
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

// Each pixel is about as wide as the terrain model's own detail, so a more detailed level only fetches more.
private const val ZOOM = 12
private const val TILE_SIZE = 256

// The open Terrain Tiles dataset, in the encoding that keeps heights in a PNG's colours.
private const val URL_PATTERN = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/%d/%d/%d.png"

// The terrain model does not change; this only renews what was fetched long ago.
private val MAX_AGE = 365.days

private const val CACHE_LIMIT_BYTES = 200L * 1024 * 1024

private val logger = KotlinLogging.logger {}

/**
 * Reads ground heights from Terrain Tiles kept in [directory] (ADR 0028). A tile stores the height of each pixel in
 * its red, green and blue values; a point between pixel centres gets the height in between.
 */
class TerrariumElevationSource(
    directory: Path,
    userAgent: String,
    get: suspend (url: String, userAgent: String) -> TileAnswer = ::httpGetTile,
    now: () -> Instant = { Clock.System.now() },
) : TerrainElevationSource {
    private val cache =
        TileCache(directory, userAgent, ::urlOf, get, now, MAX_AGE, CACHE_LIMIT_BYTES)

    override suspend fun elevations(points: List<GeoPoint>): List<Int?> {
        val pixels = points.map { it.toPixel() }
        val tiles =
            pixels
                .map { it.tile }
                .distinct()
                .associateWith { cache.tile(it)?.let { bytes -> decode(it, bytes) } }
        return pixels.map { pixel -> tiles[pixel.tile]?.let { heightAt(it, pixel.x, pixel.y) } }
    }

    private suspend fun decode(
        tile: TileCoordinates,
        bytes: ByteArray,
    ): BufferedImage? =
        withContext(Dispatchers.IO) {
            try {
                ImageIO.read(ByteArrayInputStream(bytes))
            } catch (e: IOException) {
                null.also { logger.warn(e) { "The terrain tile $tile could not be read" } }
            }
        }

    private fun urlOf(coordinates: TileCoordinates): String =
        URL_PATTERN.format(coordinates.zoom, coordinates.x, coordinates.y)
}

/** A point within its tile, in pixels from the tile's top left corner. */
private class TilePixel(
    val tile: TileCoordinates,
    val x: Double,
    val y: Double,
)

private fun GeoPoint.toPixel(): TilePixel {
    val worldSize = TILE_SIZE.toDouble() * (1 shl ZOOM)
    val worldX = (longitude + HALF_TURN) / FULL_TURN * worldSize
    val worldY = (1 - asinh(tan(latitude / HALF_TURN * PI)) / PI) / 2 * worldSize
    val tileX = floor(worldX / TILE_SIZE).toInt()
    val tileY = floor(worldY / TILE_SIZE).toInt()
    return TilePixel(TileCoordinates(ZOOM, tileX, tileY), worldX - tileX * TILE_SIZE, worldY - tileY * TILE_SIZE)
}

/** Interpolated between the four nearest pixel centres; at a tile's edge the edge pixels stand in for the neighbour. */
private fun heightAt(
    image: BufferedImage,
    x: Double,
    y: Double,
): Int {
    val left = floor(x - HALF_PIXEL).toInt()
    val top = floor(y - HALF_PIXEL).toInt()
    val dx = x - HALF_PIXEL - left
    val dy = y - HALF_PIXEL - top

    fun at(
        px: Int,
        py: Int,
    ) = image.heightOf(px.coerceIn(0, image.width - 1), py.coerceIn(0, image.height - 1))
    val upper = at(left, top) * (1 - dx) + at(left + 1, top) * dx
    val lower = at(left, top + 1) * (1 - dx) + at(left + 1, top + 1) * dx
    return (upper * (1 - dy) + lower * dy).roundToInt()
}

private fun BufferedImage.heightOf(
    x: Int,
    y: Int,
): Double {
    val rgb = getRGB(x, y)
    val red = rgb shr RED_SHIFT and BYTE
    val green = rgb shr GREEN_SHIFT and BYTE
    val blue = rgb and BYTE
    return red * BYTE_VALUES + green + blue / BYTE_VALUES.toDouble() - TERRARIUM_OFFSET
}

private const val HALF_TURN = 180.0
private const val FULL_TURN = 360.0
private const val HALF_PIXEL = 0.5
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BYTE = 0xFF
private const val BYTE_VALUES = 256
private const val TERRARIUM_OFFSET = 32768
