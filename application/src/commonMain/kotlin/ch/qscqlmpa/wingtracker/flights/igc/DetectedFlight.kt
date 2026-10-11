package ch.qscqlmpa.wingtracker.flights.igc

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Take-off and landing found in a track. A logger is often switched on well before take-off and off well after
 * landing, so the flight is the stretch where the track really moves, not the whole recording.
 */
data class DetectedFlight(
    val takeOff: Instant,
    val landing: Instant,
) {
    val duration: Duration get() = landing - takeOff

    /** Flights are logged in whole minutes. */
    val durationInMinutes: Duration get() =
        (duration.inWholeSeconds + SECONDS_PER_MINUTE / 2)
            .div(
                SECONDS_PER_MINUTE,
            ).minutes
}

private const val SECONDS_PER_MINUTE = 60L
