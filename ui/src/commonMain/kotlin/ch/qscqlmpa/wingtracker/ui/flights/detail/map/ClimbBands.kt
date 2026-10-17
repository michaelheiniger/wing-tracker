package ch.qscqlmpa.wingtracker.ui.flights.detail.map

import ch.qscqlmpa.wingtracker.flights.igc.AnalysedTrack
import ch.qscqlmpa.wingtracker.flights.igc.TrackFix

// Where one band ends and the next begins, in metres per second.
const val WEAK_CLIMB_FROM = 0.0
const val MODERATE_CLIMB_FROM = 1.0
const val STRONG_CLIMB_FROM = 2.5

/** How the pilot climbed or sank, in the steps the map colours the track by. */
enum class ClimbBand {
    Sink,
    WeakClimb,
    ModerateClimb,
    StrongClimb,
}

fun climbBandOf(metersPerSecond: Double): ClimbBand =
    when {
        metersPerSecond < WEAK_CLIMB_FROM -> ClimbBand.Sink
        metersPerSecond < MODERATE_CLIMB_FROM -> ClimbBand.WeakClimb
        metersPerSecond < STRONG_CLIMB_FROM -> ClimbBand.ModerateClimb
        else -> ClimbBand.StrongClimb
    }

/** A stretch of the flight in one band; neighbouring stretches share their end fix, so the line has no gap. */
data class ClimbRun(
    val band: ClimbBand,
    val fixes: List<TrackFix>,
)

/**
 * The flight cut where the band changes. Each fix takes the climb rate measured from it or, where none was, the last
 * one before it. Empty when the track has no climb rates, e.g. a logger without altitudes.
 */
fun AnalysedTrack.climbRuns(): List<ClimbRun> {
    if (climbRates.isEmpty()) return emptyList()
    val runs = mutableListOf<ClimbRun>()
    var current = mutableListOf<TrackFix>()
    var currentBand: ClimbBand? = null
    var rate = 0
    for (fix in flown) {
        while (rate < climbRates.lastIndex && climbRates[rate + 1].from <= fix.time) rate++
        val band = climbBandOf(climbRates[rate].metersPerSecond)
        if (band != currentBand && currentBand != null) {
            current += fix
            runs += ClimbRun(currentBand, current)
            current = mutableListOf()
        }
        currentBand = band
        current += fix
    }
    currentBand?.let { if (current.size >= 2) runs += ClimbRun(it, current) }
    return runs
}
