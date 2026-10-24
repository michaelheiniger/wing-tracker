package ch.qscqlmpa.wingtracker.gear.maintenance

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions

class UpdateCheck(
    private val gear: MaintainedGearRepository,
    private val attachments: AttachmentStore,
    private val clock: AppClock,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(
        ref: GearRef,
        checkId: CheckId,
        details: CheckDetails,
        newFiles: List<NewAttachment> = emptyList(),
        removedFiles: Set<AttachmentId> = emptySet(),
    ): MaintainedGear =
        transactions.run(LogbookChange.CheckUpdated(ref, checkId)) {
            val item = gear.require(ref)
            val existing = item.history.checkOf(checkId)
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
                    item.withHistory(item.history.withUpdatedCheck(check)).also { gear.save(it) }
                }
            // Only after the aggregate no longer references them are the removed files deleted.
            existing.reports.filter { it.id in removedFiles }.forEach { attachments.delete(it.id) }
            updated
        }
}
