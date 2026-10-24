package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.importing.FlightImporter
import ch.qscqlmpa.wingtracker.flights.importing.ImportResult
import ch.qscqlmpa.wingtracker.flights.importing.NewWingSpec
import ch.qscqlmpa.wingtracker.flights.importing.WingTarget
import ch.qscqlmpa.wingtracker.gear.wing.RegisterWing
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Brings the chosen Syride flights in, creating the wings that were asked for. Importing again is safe because
 * every imported flight remembers its Syride id.
 */
@OptIn(ExperimentalUuidApi::class)
class ImportSyrideFlights(
    private val flights: FlightRepository,
    wings: WingRepository,
    clock: AppClock,
    registerWing: RegisterWing,
    private val transactions: LogbookTransactions,
    // Injectable so tests get deterministic ids.
    newId: () -> FlightId = { FlightId(Uuid.random().toString()) },
) {
    private val importer = FlightImporter(flights, wings, clock, registerWing, newId)

    suspend operator fun invoke(
        selection: List<Pair<SyrideFlight, WingTarget>>,
        newWings: Map<String, NewWingSpec>,
    ): ImportResult<SyrideFlight> =
        transactions.run(LogbookChange.FlightsImportedFromSyride) {
            val alreadyImported = importedIds()
            val chosen =
                selection.filter { (flight, target) ->
                    target != WingTarget.Skip &&
                        flight.id !in alreadyImported
                }
            // Oldest first: flights of one day are ordered by when they were logged, so this keeps their real order.
            val ordered = chosen.sortedWith(compareBy({ it.first.date }, { it.first.startTime }))
            importer.import(ordered, newWings, skipped = selection.size - chosen.size)
        }

    private suspend fun importedIds(): Set<String> =
        flights
            .findAll()
            .items
            .mapNotNull { it.importedFrom }
            .filter { it.system == SYRIDE_SYSTEM }
            .map { it.id }
            .toSet()
}
