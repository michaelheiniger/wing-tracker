package ch.qscqlmpa.wingtracker.gear.wing.maintenance

import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository

class DeleteCheck(
    private val wings: WingRepository,
    private val attachments: AttachmentStore,
) {
    suspend operator fun invoke(
        wingId: WingId,
        checkId: CheckId,
    ): Wing {
        val wing = wings.require(wingId)
        val check = wing.checkOf(checkId)
        val updated = wing.withoutCheck(checkId)
        wings.save(updated)
        // Files go last: if deleting one fails, the history is already consistent and only a stray file remains.
        check.reports.forEach { attachments.delete(it.id) }
        return updated
    }
}
