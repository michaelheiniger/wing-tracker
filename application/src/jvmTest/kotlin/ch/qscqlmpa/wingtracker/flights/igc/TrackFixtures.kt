// Builds synthetic tracks from legs: a pilot standing, walking or flying east in a straight line.
package ch.qscqlmpa.wingtracker.flights.igc

import kotlin.math.cos
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val START_LATITUDE = 46.7
private const val START_LONGITUDE = 7.8
private const val METERS_PER_DEGREE = 111_320.0
private val FIX_INTERVAL = 5.seconds

val TRACK_START: Instant = Instant.parse("2026-09-20T09:00:00Z")

fun trackOf(vararg legs: Leg): List<TrackFix> {
    val fixes = mutableListOf(TrackFix(TRACK_START, START_LATITUDE, START_LONGITUDE))
    var time = TRACK_START
    var longitude = START_LONGITUDE
    legs.forEach { leg ->
        val steps = (leg.duration / FIX_INTERVAL).toInt()
        repeat(steps) {
            time += FIX_INTERVAL
            val meters = leg.speedKmh / 3.6 * FIX_INTERVAL.inWholeSeconds
            longitude += meters / (METERS_PER_DEGREE * cos(Math.toRadians(START_LATITUDE)))
            fixes += TrackFix(time, START_LATITUDE, longitude)
        }
    }
    return fixes
}

/** The moment a leg starts, counted from the start of the track. */
fun at(offset: Duration): Instant = TRACK_START + offset
