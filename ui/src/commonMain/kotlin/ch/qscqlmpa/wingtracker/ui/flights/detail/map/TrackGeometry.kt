package ch.qscqlmpa.wingtracker.ui.flights.detail.map

import ch.qscqlmpa.wingtracker.flights.igc.AnalysedTrack
import ch.qscqlmpa.wingtracker.flights.igc.TrackFix
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/** A point of the whole map, both sides from 0 to 1 with y pointing south, as tile maps lay the world out. */
data class MapPoint(
    val x: Double,
    val y: Double,
)

/** Web Mercator, the projection of the tiles. */
fun TrackFix.toMapPoint(): MapPoint {
    val latitudeRadians = latitude / DEGREES_PER_HALF_TURN * PI
    return MapPoint(
        x = (longitude + DEGREES_PER_HALF_TURN) / DEGREES_PER_TURN,
        y = (1 - asinh(tan(latitudeRadians)) / PI) / 2,
    )
}

/**
 * The recording split at take-off and landing. The parts share their end fixes, so the drawn line has no gap. A
 * recording without a flight is all [before].
 */
data class TrackSegments(
    val before: List<TrackFix>,
    val flown: List<TrackFix>,
    val after: List<TrackFix>,
)

fun AnalysedTrack.segments(): TrackSegments {
    val flight = flight ?: return TrackSegments(before = fixes, flown = emptyList(), after = emptyList())
    return TrackSegments(
        before = fixes.filter { it.time <= flight.takeOff },
        flown = flown,
        after = fixes.filter { it.time >= flight.landing },
    )
}

data class MapArea(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

/** The area the map opens on: the flight, or the whole recording when it has none. */
fun AnalysedTrack.focusArea(): MapArea? {
    val points = flown.ifEmpty { fixes }.map { it.toMapPoint() }
    if (points.isEmpty()) return null
    return points.fold(MapArea(points[0].x, points[0].y, points[0].x, points[0].y)) { area, p ->
        MapArea(min(area.left, p.x), min(area.top, p.y), max(area.right, p.x), max(area.bottom, p.y))
    }
}

private const val DEGREES_PER_HALF_TURN = 180.0
private const val DEGREES_PER_TURN = 360.0
