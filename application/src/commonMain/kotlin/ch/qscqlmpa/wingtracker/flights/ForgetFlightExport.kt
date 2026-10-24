package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions

/**
 * Removes the record that a flight was exported to a system, so it is sent again by the next export. Only the record
 * in the logbook changes; nothing is deleted in the other system.
 */
class ForgetFlightExport(
    private val flights: FlightRepository,
    private val transactions: LogbookTransactions,
) {
    /** Returns the forgotten record, to put back with [restore]; null when there was none. */
    suspend operator fun invoke(
        id: FlightId,
        system: String,
    ): FlightExport? =
        transactions.run(LogbookChange.FlightExportForgotten(id, system)) {
            val flight = flights.findById(id) ?: throw FlightNotFoundException(id)
            flight.exportTo(system)?.also { flights.save(flight.forgetExport(system)) }
        }

    /** Undoes [invoke]; the flight is read again, so changes made meanwhile are kept. */
    suspend fun restore(
        id: FlightId,
        export: FlightExport,
    ) = transactions.run(LogbookChange.FlightExportRestored(id, export.system)) {
        flights.findById(id)?.let { flights.save(it.restoreExport(export)) }
    }
}
