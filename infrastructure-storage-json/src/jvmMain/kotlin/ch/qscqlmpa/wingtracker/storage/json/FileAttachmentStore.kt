package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val logger = KotlinLogging.logger {}

/**
 * Keeps attached files in the data folder, named by id so the original name never touches the file system
 * (ADR 0011).
 */
class FileAttachmentStore(
    dataDirectory: Path,
) : AttachmentStore {
    private val directory = dataDirectory.resolve("attachments")

    override suspend fun save(
        id: AttachmentId,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        Files.createDirectories(directory)
        // Written to a temporary file first so a crash never leaves a half-written attachment behind.
        val temporary = Files.createTempFile(directory, "${id.value}-", ".tmp")
        try {
            Files.write(temporary, bytes)
            Files.move(temporary, directory.resolve(id.value), StandardCopyOption.REPLACE_EXISTING)
            logger.debug { "Saved attachment ${id.value} (${bytes.size} bytes)" }
        } finally {
            Files.deleteIfExists(temporary)
        }
        Unit
    }

    override suspend fun read(id: AttachmentId): ByteArray =
        withContext(Dispatchers.IO) {
            Files.readAllBytes(directory.resolve(id.value))
        }

    override suspend fun delete(id: AttachmentId) =
        withContext(Dispatchers.IO) {
            Files.deleteIfExists(directory.resolve(id.value))
            logger.debug { "Deleted attachment ${id.value}" }
        }
}
