package ch.qscqlmpa.wingtracker.flights.igc

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Ground speed above which the pilot counts as moving. Walking uphill stays below it (hike and fly), flying
 * including slow thermalling stays above it.
 */
private const val MOVING_SPEED_KMH = 8.0

// Speed is measured over this much time, so one noisy fix cannot start or end a flight.
private val SPEED_WINDOW = 20.seconds

// Standing still this long means the pilot has landed; moving again afterwards
// (e.g. a car ride back) is not the flight.
private val LANDING_GAP = 5.minutes
private val MIN_FLIGHT = 1.minutes

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val METERS_PER_SECOND_TO_KMH = 3.6
private const val HALF = 0.5

/** Returns the first stretch of sustained movement, or null when the track never really moves. */
fun detectFlight(fixes: List<TrackFix>): DetectedFlight? {
    val intervals = movingIntervals(fixes)
    if (intervals.isEmpty()) return null
    val start = intervals.first().first
    var end = intervals.first().second
    for ((nextStart, nextEnd) in intervals.drop(1)) {
        if (nextStart - end >= LANDING_GAP) break
        if (nextEnd > end) end = nextEnd
    }
    val flight = DetectedFlight(start, end)
    return flight.takeIf { it.duration >= MIN_FLIGHT }
}

/**
 * For each fix, the interval to the first fix at least [SPEED_WINDOW] later,
 * if the pilot moved fast enough over it.
 */
private fun movingIntervals(fixes: List<TrackFix>): List<Pair<Instant, Instant>> {
    val intervals = mutableListOf<Pair<Instant, Instant>>()
    var k = 0
    for (i in fixes.indices) {
        if (k < i) k = i
        while (k < fixes.lastIndex && fixes[k].time - fixes[i].time < SPEED_WINDOW) k++
        val seconds = (fixes[k].time - fixes[i].time).inWholeMilliseconds / MILLIS_PER_SECOND
        if (k == i || seconds < MIN_MEASURE_SECONDS) continue
        val speedKmh = distanceMeters(fixes[i], fixes[k]) / seconds * METERS_PER_SECOND_TO_KMH
        if (speedKmh >= MOVING_SPEED_KMH) intervals += fixes[i].time to fixes[k].time
    }
    return intervals
}

private const val MILLIS_PER_SECOND = 1000.0
private const val MIN_MEASURE_SECONDS = 5.0

/** Great-circle distance (haversine); plenty accurate for judging whether someone is moving. */
internal fun distanceMeters(
    from: TrackFix,
    to: TrackFix,
): Double {
    val lat1 = toRadians(from.latitude)
    val lat2 = toRadians(to.latitude)
    val dLat = lat2 - lat1
    val dLon = toRadians(to.longitude - from.longitude)
    val a = sin(dLat * HALF).pow(2) + cos(lat1) * cos(lat2) * sin(dLon * HALF).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
}

private fun toRadians(degrees: Double) = degrees / DEGREES_PER_HALF_TURN * kotlin.math.PI

private const val DEGREES_PER_HALF_TURN = 180.0
