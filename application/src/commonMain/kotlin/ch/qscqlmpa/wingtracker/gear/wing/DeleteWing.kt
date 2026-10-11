package ch.qscqlmpa.wingtracker.gear.wing

/** Lets the flights context veto deletions without the gear context knowing how flights are stored. */
fun interface WingFlightLookup {
    suspend fun hasFlights(id: WingId): Boolean
}

class WingInUseException(
    id: WingId,
) : IllegalStateException("Wing ${id.value} has flights and can only be retired")

/** Deleting is for wings entered by mistake; a wing with flights is retired so the logbook stays intact. */
class DeleteWing(
    private val wings: WingRepository,
    private val flights: WingFlightLookup,
) {
    suspend operator fun invoke(id: WingId) {
        if (wings.findById(id) == null) throw WingNotFoundException(id)
        if (flights.hasFlights(id)) throw WingInUseException(id)
        wings.delete(id)
    }
}
