package ch.qscqlmpa.wingtracker.gear.wing.maintenance

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository

class UpdateCheck(
    private val wings: WingRepository,
    private val attachments: AttachmentStore,
    private val clock: AppClock,
) {
    suspend operator fun invoke(
        wingId: WingId,
        checkId: CheckId,
        details: CheckDetails,
        newFiles: List<NewAttachment> = emptyList(),
        removedFiles: Set<AttachmentId> = emptySet(),
    ): Wing {
        val wing = wings.require(wingId)
        val existing = wing.checkOf(checkId)
        if (details.date > clock.today()) throw CheckDateInFutureException()
        val updated =
            withStoredFiles(attachments, newFiles) { added ->
                val check =
                    existing.copy(
                        date = details.date,
                        type = details.type,
                        company = details.company.cleaned(),
                        notes = details.notes.cleaned(),
                        restartsInterval = details.restartsInterval,
                        reports = existing.reports.filter { it.id !in removedFiles } + added,
                    )
                wing.withUpdatedCheck(check).also { wings.save(it) }
            }
        // Only after the aggregate no longer references them are the removed files deleted.
        existing.reports.filter { it.id in removedFiles }.forEach { attachments.delete(it.id) }
        return updated
    }
}
