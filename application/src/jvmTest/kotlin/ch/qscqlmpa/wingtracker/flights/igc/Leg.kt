package ch.qscqlmpa.wingtracker.flights.igc

import kotlin.time.Duration

// Synthetic tracks: a pilot standing, walking or flying east in a straight line, one fix every five seconds.
sealed interface Leg {
    val duration: Duration
    val speedKmh: Double

    data class Still(
        override val duration: Duration,
    ) : Leg {
        override val speedKmh = 0.0
    }

    data class Moving(
        override val duration: Duration,
        override val speedKmh: Double,
    ) : Leg
}
