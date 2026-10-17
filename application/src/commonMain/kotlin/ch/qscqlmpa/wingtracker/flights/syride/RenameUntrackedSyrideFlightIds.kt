package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.FlightRepository

// The prefix versions up to 0.2.0 stored for Syride flights without a track, in Syride's vocabulary.
private const val LEGACY_UNTRACKED_FLIGHT_PREFIX = "activity:"

/**
 * Gives Syride flights without a track, imported by an earlier version, the id the Syride import uses now, so it still
 * recognises them and does not import them again. Safe to run on every start: once done, it changes nothing.
 */
class RenameUntrackedSyrideFlightIds(
    private val flights: FlightRepository,
) {
    /** Returns how many flights were renamed. */
    suspend operator fun invoke(): Int =
        flights.findAll().items.count { flight ->
            val reference = flight.importedFrom
            if (reference?.system != SYRIDE_SYSTEM || !reference.id.startsWith(LEGACY_UNTRACKED_FLIGHT_PREFIX)) {
                return@count false
            }
            val number = reference.id.removePrefix(LEGACY_UNTRACKED_FLIGHT_PREFIX)
            flights.save(flight.copy(importedFrom = reference.copy(id = "$UNTRACKED_FLIGHT_PREFIX$number")))
            true
        }
}
