package ch.qscqlmpa.wingtracker.flights.igc

import ch.qscqlmpa.wingtracker.maps.GeoPoint
import ch.qscqlmpa.wingtracker.maps.TerrainElevationSource
import kotlin.time.Instant

// More points than a profile has pixels only cost lookups; fewer would cut off narrow ridges and gullies.
private const val MAX_GROUND_POINTS = 600

data class GroundPoint(
    val time: Instant,
    val elevationMeters: Int,
)

/** The ground under the track over time; [complete] is false when part of it could not be had, e.g. offline. */
data class GroundProfile(
    val points: List<GroundPoint>,
    val complete: Boolean,
) {
    /** The ground at [time], between the two nearest points; null outside the profile. */
    fun elevationAt(time: Instant): Double? {
        val after = points.indexOfFirst { it.time >= time }
        if (after < 0) return null
        val next = points[after]
        if (next.time == time) return next.elevationMeters.toDouble()
        val previous = points.getOrNull(after - 1) ?: return null
        val share = (time - previous.time) / (next.time - previous.time)
        return previous.elevationMeters + (next.elevationMeters - previous.elevationMeters) * share
    }
}

/** The ground under a track, worked out again whenever it is looked at, like the track itself. */
class ReadGroundProfile(
    private val terrain: TerrainElevationSource,
) {
    suspend operator fun invoke(track: AnalysedTrack): GroundProfile {
        val sampled = sample(track.fixes)
        val elevations = terrain.elevations(sampled.map { GeoPoint(it.latitude, it.longitude) })
        val points =
            sampled.zip(elevations).mapNotNull { (fix, elevation) ->
                elevation?.let { GroundPoint(fix.time, it) }
            }
        return GroundProfile(points, complete = points.size == sampled.size)
    }

    private fun sample(fixes: List<TrackFix>): List<TrackFix> {
        if (fixes.size <= MAX_GROUND_POINTS) return fixes
        val step = (fixes.size - 1).toDouble() / (MAX_GROUND_POINTS - 1)
        return List(MAX_GROUND_POINTS) { fixes[(it * step).toInt()] }
    }
}
