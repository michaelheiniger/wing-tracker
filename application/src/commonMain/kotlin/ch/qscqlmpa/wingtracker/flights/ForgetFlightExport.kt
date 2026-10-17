package ch.qscqlmpa.wingtracker.flights

/**
 * Removes the record that a flight was exported to a system, so it is sent again by the next export. Only the record
 * in the logbook changes; nothing is deleted in the other system.
 */
class ForgetFlightExport(
    private val flights: FlightRepository,
) {
    /** Returns the forgotten record, to put back with [restore]; null when there was none. */
    suspend operator fun invoke(
        id: FlightId,
        system: String,
    ): FlightExport? {
        val flight = flights.findById(id) ?: throw FlightNotFoundException(id)
        val export = flight.exportTo(system) ?: return null
        flights.save(flight.forgetExport(system))
        return export
    }

    /** Undoes [invoke]; the flight is read again, so changes made meanwhile are kept. */
    suspend fun restore(
        id: FlightId,
        export: FlightExport,
    ) {
        val flight = flights.findById(id) ?: return
        flights.save(flight.restoreExport(export))
    }
}
