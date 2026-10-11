package ch.qscqlmpa.wingtracker.gear.maintenance

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun newAttachmentId() = AttachmentId(Uuid.random().toString())

/**
 * Stores [files] and returns their references. If anything fails afterwards, [block] must not leave
 * the stored bytes behind, so callers run their save inside [withStoredFiles].
 */
internal suspend fun <T> withStoredFiles(
    store: AttachmentStore,
    files: List<NewAttachment>,
    block: suspend (List<Attachment>) -> T,
): T {
    val stored = mutableListOf<Attachment>()
    var committed = false
    try {
        files.forEach { file ->
            val id = newAttachmentId()
            store.save(id, file.bytes)
            stored += Attachment(id, file.fileName)
        }
        return block(stored).also { committed = true }
    } finally {
        // The aggregate was never saved, so nothing references these files: remove them instead of leaking,
        // even when the caller was cancelled.
        if (!committed) withContext(NonCancellable) { stored.forEach { store.delete(it.id) } }
    }
}

@OptIn(ExperimentalUuidApi::class)
class RecordCheck(
    private val gear: MaintainedGearRepository,
    private val attachments: AttachmentStore,
    private val clock: AppClock,
    // Injectable so tests get deterministic ids.
    private val newId: () -> CheckId = { CheckId(Uuid.random().toString()) },
) {
    /** [completes] turns a planned check into history in one save, so it can never be both. */
    suspend operator fun invoke(
        ref: GearRef,
        details: CheckDetails,
        files: List<NewAttachment> = emptyList(),
        completes: PlannedCheckId? = null,
    ): MaintainedGear {
        val item = gear.require(ref)
        if (details.date > clock.today()) throw CheckDateInFutureException()
        return withStoredFiles(attachments, files) { reports ->
            val check =
                MaintenanceCheck(
                    id = newId(),
                    date = details.date,
                    type = details.type,
                    company = details.company.cleaned(),
                    notes = details.notes.cleaned(),
                    restartsInterval = details.restartsInterval,
                    reports = reports,
                )
            val withCheck = item.history.withCheck(check)
            val history = if (completes == null) withCheck else withCheck.withoutPlannedCheck(completes)
            item.withHistory(history).also { gear.save(it) }
        }
    }
}

internal fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
