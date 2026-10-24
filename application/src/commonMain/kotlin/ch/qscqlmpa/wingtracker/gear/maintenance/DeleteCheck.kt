package ch.qscqlmpa.wingtracker.gear.maintenance

import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions

class DeleteCheck(
    private val gear: MaintainedGearRepository,
    private val attachments: AttachmentStore,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(
        ref: GearRef,
        checkId: CheckId,
    ): MaintainedGear =
        transactions.run(LogbookChange.CheckDeleted(ref, checkId)) {
            val item = gear.require(ref)
            val check = item.history.checkOf(checkId)
            val updated = item.withHistory(item.history.withoutCheck(checkId))
            gear.save(updated)
            // Files go last: if deleting one fails, the history is already consistent and only a stray file remains.
            check.reports.forEach { attachments.delete(it.id) }
            updated
        }
}
