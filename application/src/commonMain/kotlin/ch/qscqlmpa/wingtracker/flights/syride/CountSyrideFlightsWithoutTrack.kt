package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.FlightRepository

/**
 * Counts the Syride flights in the logbook that have a track on Syride but none stored yet. The Syride import offers to
 * add those tracks right away, since they then attach to these flights (ADR 0026).
 */
class CountSyrideFlightsWithoutTrack(
    private val flights: FlightRepository,
) {
    suspend operator fun invoke(): Int =
        flights.findAll().items.count { it.igcFile == null && it.importedFrom?.isSyrideFlightWithTrack() == true }
}
