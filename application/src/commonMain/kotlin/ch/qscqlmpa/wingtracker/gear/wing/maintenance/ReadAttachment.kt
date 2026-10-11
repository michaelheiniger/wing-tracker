package ch.qscqlmpa.wingtracker.gear.wing.maintenance

import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore

class ReadAttachment(
    private val attachments: AttachmentStore,
) {
    suspend operator fun invoke(id: AttachmentId): ByteArray = attachments.read(id)
}
