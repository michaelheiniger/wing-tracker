package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions

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
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(id: WingId) =
        transactions.run(LogbookChange.GearDeleted(id.ref)) {
            val wing = wings.findById(id) ?: throw WingNotFoundException(id)
            if (wing.checks.isNotEmpty() || flights.hasFlights(id)) throw WingInUseException(id)
            wings.delete(id)
        }
}

internal val WingId.ref get() = GearRef(GearKind.Wing, value)
