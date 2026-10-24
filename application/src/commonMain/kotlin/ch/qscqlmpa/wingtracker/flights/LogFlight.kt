package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class LogFlight(
    private val flights: FlightRepository,
    wings: WingRepository,
    private val clock: AppClock,
    private val transactions: LogbookTransactions,
    // Injectable so tests get deterministic ids.
    private val newId: () -> FlightId = { FlightId(Uuid.random().toString()) },
) {
    private val rules = FlightRules(wings, clock)

    suspend operator fun invoke(details: FlightDetails): Flight {
        val id = newId()
        return transactions.run(LogbookChange.FlightLogged(id)) {
            rules.ensureAllowed(details)
            Flight(id, details, clock.now()).also { flights.save(it) }
        }
    }
}
