package ch.qscqlmpa.wingtracker.maps

class ReadMapTile(
    private val source: MapTileSource,
) {
    val maxZoom: Int get() = source.maxZoom

    /** Null for a tile outside the grid, which a map can ask for at its edges, so it is never requested. */
    suspend operator fun invoke(coordinates: TileCoordinates): ByteArray? =
        if (isInGrid(coordinates)) source.tile(coordinates) else null

    private fun isInGrid(coordinates: TileCoordinates): Boolean {
        val (zoom, x, y) = coordinates
        if (zoom !in 0..source.maxZoom) return false
        val tilesPerSide = 1 shl zoom
        return x in 0 until tilesPerSide && y in 0 until tilesPerSide
    }
}
