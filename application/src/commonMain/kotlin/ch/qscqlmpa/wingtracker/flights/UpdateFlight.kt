package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions

class UpdateFlight(
    private val flights: FlightRepository,
    wings: WingRepository,
    clock: AppClock,
    private val transactions: LogbookTransactions,
) {
    private val rules = FlightRules(wings, clock)

    suspend operator fun invoke(
        id: FlightId,
        details: FlightDetails,
    ): Flight =
        transactions.run(LogbookChange.FlightUpdated(id)) {
            val existing = flights.findById(id) ?: throw FlightNotFoundException(id)
            rules.ensureAllowed(details)
            existing.withDetails(details).also { flights.save(it) }
        }
}
