package ch.qscqlmpa.wingtracker.gear.wing

/** Lets the flights context veto deletions without the gear context knowing how flights are stored. */
fun interface WingFlightLookup {
    suspend fun hasFlights(id: WingId): Boolean
}

/** Gear that has history is retired rather than deleted, so reports and logbooks are never lost by accident. */
open class GearInUseException(
    message: String,
) : IllegalStateException(message)

class WingInUseException(
    id: WingId,
) : GearInUseException("Wing ${id.value} has flights or maintenance history and can only be retired")

/** Deleting is for wings entered by mistake; a wing with flights or checks is retired so history stays intact. */
class DeleteWing(
    private val wings: WingRepository,
    private val flights: WingFlightLookup,
) {
    suspend operator fun invoke(id: WingId) {
        val wing = wings.findById(id) ?: throw WingNotFoundException(id)
        if (wing.checks.isNotEmpty() || flights.hasFlights(id)) throw WingInUseException(id)
        wings.delete(id)
    }
}
