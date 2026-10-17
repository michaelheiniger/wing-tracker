package ch.qscqlmpa.wingtracker.ui.flights.detail.profile

import ch.qscqlmpa.wingtracker.flights.igc.TrackFix
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Steps a pilot reads at a glance; the smallest that keeps the labels from crowding is used.
private val TIME_STEPS = listOf(5.minutes, 10.minutes, 15.minutes, 30.minutes, 1.hours, 2.hours, 4.hours)

/** Round moments between [start] and [end] for the time axis, at most [maxTicks] of them. */
fun timeTicks(
    start: Instant,
    end: Instant,
    maxTicks: Int,
): List<Instant> {
    val span = end - start
    val step = TIME_STEPS.firstOrNull { span / it <= maxTicks.toDouble() } ?: TIME_STEPS.last()
    val first = start.roundedUpTo(step)
    return generateSequence(first) { it + step }.takeWhile { it <= end }.toList()
}

private fun Instant.roundedUpTo(step: Duration): Instant {
    val stepSeconds = step.inWholeSeconds
    val seconds = epochSeconds + (if (nanosecondsOfSecond > 0) 1 else 0)
    val rounded = (seconds + stepSeconds - 1).floorDiv(stepSeconds) * stepSeconds
    return Instant.fromEpochSeconds(rounded)
}

/** The fix closest in time to [time]; the fixes are in chronological order, as tracks keep them. */
fun List<TrackFix>.nearestTo(time: Instant): TrackFix? {
    if (isEmpty()) return null
    val index = binarySearch { it.time.compareTo(time) }
    if (index >= 0) return this[index]
    val after = -index - 1
    val candidates = listOfNotNull(getOrNull(after - 1), getOrNull(after))
    return candidates.minByOrNull { (it.time - time).absoluteValue }
}
