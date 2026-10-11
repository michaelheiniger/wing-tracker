package ch.qscqlmpa.wingtracker.attachments

/**
 * Port for the bytes of attached files (ADR 0011). Aggregates only keep an [Attachment] reference,
 * so the bytes can live in a folder today and in cloud storage later.
 */
interface AttachmentStore {
    suspend fun save(
        id: AttachmentId,
        bytes: ByteArray,
    )

    suspend fun read(id: AttachmentId): ByteArray

    /** Deleting an attachment that is already gone is not an error, so cleanups can be repeated safely. */
    suspend fun delete(id: AttachmentId)
}

/** What an aggregate remembers about an attached file; the original name is kept for display and saving. */
data class Attachment(
    val id: AttachmentId,
    val fileName: String,
) {
    init {
        require(fileName.isNotBlank()) { "An attachment needs a file name" }
    }
}
