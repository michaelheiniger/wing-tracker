package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions

class UpdateWingDetails(
    private val wings: WingRepository,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(
        id: WingId,
        details: WingDetails,
    ): Wing =
        transactions.run(LogbookChange.GearDetailsUpdated(id.ref)) {
            (wings.findById(id) ?: throw WingNotFoundException(id)).withDetails(details).also { wings.save(it) }
        }
}
