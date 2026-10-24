package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions
import kotlinx.datetime.LocalDate

class RetireWing(
    private val wings: WingRepository,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(
        id: WingId,
        on: LocalDate,
        reason: String?,
    ): Wing =
        transactions.run(LogbookChange.GearRetired(id.ref)) {
            (wings.findById(id) ?: throw WingNotFoundException(id)).retire(on, reason).also { wings.save(it) }
        }
}
