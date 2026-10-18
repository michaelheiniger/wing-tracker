package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.backup.BackupArchive
import ch.qscqlmpa.wingtracker.backup.BackupException
import ch.qscqlmpa.wingtracker.backup.BackupProblem
import ch.qscqlmpa.wingtracker.backup.BackupSummary
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

private const val BUFFER_BYTES = 64 * 1024

/** Backs up the data folder as one zip of its files as they are stored, with a manifest of them (ADR 0031). */
class ZipBackupArchive(
    private val dataDirectory: Path,
) : BackupArchive {
    override suspend fun create(
        destination: String,
        appVersion: String,
        createdAt: Instant,
    ): BackupSummary =
        withContext(Dispatchers.IO) {
            val target = Path(destination).toAbsolutePath().normalize()
            if (isInsideDataFolder(target)) throw BackupException(BackupProblem.InsideDataFolder, destination)
            val folder = target.parent.createDirectories()
            // Written under a temporary name, so an interrupted backup never looks like a finished one.
            val temporary = Files.createTempFile(folder, "${target.fileName}-", ".tmp")
            try {
                val entries = writeArchive(temporary, appVersion, createdAt)
                moveReplacing(temporary, target)
                logger.info { "Backed up ${entries.size} files to $target" }
                BackupSummary(files = entries.size, bytes = entries.sumOf { it.size })
            } finally {
                Files.deleteIfExists(temporary)
            }
        }

    private fun writeArchive(
        archive: Path,
        appVersion: String,
        createdAt: Instant,
    ): List<BackupEntryDocument> =
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            val entries = storedFiles().map { zip.add(it) }
            val manifest =
                BackupManifestDocument(appVersion = appVersion, createdAt = createdAt.toString(), files = entries)
            zip.putNextEntry(ZipEntry(BackupManifestDocument.ENTRY_NAME))
            zip.write(json.encodeToString(manifest).encodeToByteArray())
            zip.closeEntry()
            entries
        }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private fun storedFiles(): List<Path> {
        if (!dataDirectory.exists()) return emptyList()
        // Temporary files are documents or attachments still being written; they are not stored data yet.
        return dataDirectory
            .walk()
            .filter { it.isRegularFile() && it.extension != "tmp" }
            .sorted()
            .toList()
    }

    private fun ZipOutputStream.add(file: Path): BackupEntryDocument {
        val path = file.relativeTo(dataDirectory).invariantSeparatorsPathString
        putNextEntry(ZipEntry(path))
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                write(buffer, 0, count)
                digest.update(buffer, 0, count)
                size += count
            }
        }
        closeEntry()
        return BackupEntryDocument(path, size, digest.digest().toHex())
    }

    // Compared by real path where it exists, so a link to the data folder is caught too.
    private fun isInsideDataFolder(target: Path): Boolean {
        val data = dataDirectory.realOrNormalized()
        val folder = generateSequence(target.parent) { it.parent }.firstOrNull { it.exists() } ?: return false
        val resolved = folder.realOrNormalized().resolve(target.relativeTo(folder).toString()).normalize()
        return resolved.startsWith(data)
    }

    private fun Path.realOrNormalized(): Path = if (exists()) toRealPath() else toAbsolutePath().normalize()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
