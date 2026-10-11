package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.gear.wing.WingFlightLookup
import ch.qscqlmpa.wingtracker.gear.wing.WingId

/** Answers the gear context's question "does this wing have flights?" from the flight store. */
class FlightsOfWing(
    private val flights: FlightRepository,
) : WingFlightLookup {
    override suspend fun hasFlights(id: WingId): Boolean = flights.findAll().items.any { it.details.wingId == id }
}
