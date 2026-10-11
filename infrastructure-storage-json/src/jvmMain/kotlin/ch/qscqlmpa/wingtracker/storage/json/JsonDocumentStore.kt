package ch.qscqlmpa.wingtracker.storage.json

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** A stored document's name (without extension) and raw text. */
internal data class RawDocument(
    val name: String,
    val text: String,
)

/** Plain-text document storage in one directory; knows nothing about the domain. */
internal class JsonDocumentStore(
    private val directory: Path,
    private val quarantineDirectory: Path = directory.resolve(QUARANTINE_FOLDER),
) {
    suspend fun read(name: String): String? =
        withContext(Dispatchers.IO) {
            val file = directory.resolve("$name.json")
            if (Files.exists(file)) file.readText() else null
        }

    suspend fun readAll(): List<RawDocument> =
        withContext(Dispatchers.IO) {
            if (!Files.isDirectory(directory)) return@withContext emptyList()
            directory
                .listDirectoryEntries()
                .filter { it.extension == "json" }
                .sorted()
                .map { RawDocument(it.nameWithoutExtension, it.readText()) }
        }

    /** Writes to a temporary file first so a crash never leaves a half-written document behind. */
    suspend fun write(
        name: String,
        content: String,
    ) = withContext(Dispatchers.IO) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "$name-", ".tmp")
        try {
            temporary.writeText(content)
            moveReplacing(temporary, directory.resolve("$name.json"))
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    suspend fun delete(name: String) =
        withContext(Dispatchers.IO) {
            Files.deleteIfExists(directory.resolve("$name.json"))
            Unit
        }

    /** Moves an unreadable document aside instead of deleting it, so the user can still repair it by hand. */
    suspend fun quarantine(name: String) =
        withContext(Dispatchers.IO) {
            Files.createDirectories(quarantineDirectory)
            var target = quarantineDirectory.resolve("$name.json")
            var attempt = 1
            while (Files.exists(target)) target = quarantineDirectory.resolve("$name-${attempt++}.json")
            Files.move(directory.resolve("$name.json"), target)
            Unit
        }

    private fun moveReplacing(
        source: Path,
        target: Path,
    ) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val QUARANTINE_FOLDER = "quarantine"

        /** Documents of one kind live in their own folder, and their quarantine mirrors that layout. */
        fun forCollection(
            dataDirectory: Path,
            name: String,
        ) = JsonDocumentStore(
            directory = dataDirectory.resolve(name),
            quarantineDirectory = dataDirectory.resolve(QUARANTINE_FOLDER).resolve(name),
        )
    }
}
