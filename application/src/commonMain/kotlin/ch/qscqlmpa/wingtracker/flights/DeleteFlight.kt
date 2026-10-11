package ch.qscqlmpa.wingtracker.flights

class DeleteFlight(
    private val flights: FlightRepository,
) {
    suspend operator fun invoke(id: FlightId) {
        if (flights.findById(id) == null) throw FlightNotFoundException(id)
        flights.delete(id)
    }
}
