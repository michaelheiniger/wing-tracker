package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class LogFlight(
    private val flights: FlightRepository,
    wings: WingRepository,
    private val clock: AppClock,
    // Injectable so tests get deterministic ids.
    private val newId: () -> FlightId = { FlightId(Uuid.random().toString()) },
) {
    private val rules = FlightRules(wings, clock)

    suspend operator fun invoke(details: FlightDetails): Flight {
        rules.ensureAllowed(details)
        val flight = Flight(newId(), details, clock.now())
        flights.save(flight)
        return flight
    }
}
