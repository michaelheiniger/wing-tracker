package ch.qscqlmpa.wingtracker.maps

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/** Port to a terrain model; the adapter keeps what it fetched (ADR 0028). */
interface TerrainElevationSource {
    /** The ground's height above sea level at each point, in the same order; null where it cannot be had now. */
    suspend fun elevations(points: List<GeoPoint>): List<Int?>
}
