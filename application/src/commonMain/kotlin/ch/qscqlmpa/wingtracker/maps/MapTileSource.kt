package ch.qscqlmpa.wingtracker.maps

/** A square of the Web Mercator tile grid: [zoom] 0 is the whole world in one tile, each level doubles both sides. */
data class TileCoordinates(
    val zoom: Int,
    val x: Int,
    val y: Int,
)

/** Port to whatever serves map tiles; the adapter keeps what it fetched (ADR 0027). */
interface MapTileSource {
    /** The most detailed level the source serves. */
    val maxZoom: Int

    /** The tile's image, or null when it cannot be had now, e.g. without a connection and not cached. */
    suspend fun tile(coordinates: TileCoordinates): ByteArray?
}
