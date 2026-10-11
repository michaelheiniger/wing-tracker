package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository

class UpdateFlight(
    private val flights: FlightRepository,
    wings: WingRepository,
    clock: AppClock,
) {
    private val rules = FlightRules(wings, clock)

    suspend operator fun invoke(
        id: FlightId,
        details: FlightDetails,
    ): Flight {
        val existing = flights.findById(id) ?: throw FlightNotFoundException(id)
        rules.ensureAllowed(details)
        return existing.withDetails(details).also { flights.save(it) }
    }
}
