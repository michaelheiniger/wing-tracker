package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.backup.BackupContents
import ch.qscqlmpa.wingtracker.backup.BackupException
import ch.qscqlmpa.wingtracker.backup.BackupProblem
import ch.qscqlmpa.wingtracker.backup.BackupSummary
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import java.io.Closeable
import java.io.OutputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.time.Instant

private const val BUFFER_BYTES = 64 * 1024

/**
 * Reads a backup and trusts nothing in it that its manifest does not vouch for (ADR 0031): every file must be
 * listed, with the listed size and checksum, and stay inside the folder it is unpacked into.
 */
internal class BackupZipReader private constructor(
    private val zip: ZipFile,
    private val name: String,
) : Closeable {
    val manifest: BackupManifestDocument = readManifest()

    init {
        checkListing()
    }

    val contents: BackupContents
        get() =
            BackupContents(
                createdAt = Instant.parse(manifest.createdAt),
                appVersion = manifest.appVersion,
                summary = BackupSummary(files = manifest.files.size, bytes = manifest.files.sumOf { it.size }),
            )

    /** Writes the file listed as [entry] to [out], failing when it is not exactly what the manifest lists. */
    fun copy(
        entry: BackupEntryDocument,
        out: OutputStream,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        zip.getInputStream(zip.getEntry(entry.path)).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                out.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                size += count
                // Checked as it goes, so a forged size cannot fill the disk before the end is reached.
                if (size > entry.size) damaged("${entry.path} is larger than listed")
            }
        }
        if (size != entry.size || digest.digest().toHex() != entry.sha256) damaged("${entry.path} differs")
    }

    /** Checks every file without unpacking it. */
    fun verify() = manifest.files.forEach { copy(it, OutputStream.nullOutputStream()) }

    override fun close() = zip.close()

    private fun readManifest(): BackupManifestDocument {
        val entry = zip.getEntry(BackupManifestDocument.ENTRY_NAME) ?: notABackup()
        val text = zip.getInputStream(entry).use { it.readBytes() }.decodeToString()
        return try {
            val element = json.parseToJsonElement(text)
            // The format is checked before the shape, since a newer format may hold fields this version does not know.
            val format = ((element as? JsonObject)?.get("formatVersion") as? JsonPrimitive)?.intOrNull
            if (format != null && format > BackupManifestDocument.CURRENT_FORMAT_VERSION) {
                throw BackupException(BackupProblem.NewerFormat, "$name has archive format $format")
            }
            json.decodeFromJsonElement<BackupManifestDocument>(element).also { Instant.parse(it.createdAt) }
        } catch (e: IllegalArgumentException) {
            damaged("The manifest of $name cannot be read: ${e.message}")
        }
    }

    private fun checkListing() {
        val stored =
            zip
                .entries()
                .asSequence()
                .filterNot { it.isDirectory || it.name == BackupManifestDocument.ENTRY_NAME }
                .map { it.name }
                .toList()
        val listed = manifest.files.map { it.path }
        // Sorted lists rather than sets, so a file stored or listed twice is caught too.
        if (stored.sorted() != listed.sorted()) damaged("The files in $name are not the ones its manifest lists")
        listed.firstOrNull { !it.isSafe() }?.let { damaged("$it would be unpacked outside the folder") }
    }

    // Relative, with `/` only and no step up, so unpacking can never write outside the chosen folder.
    private fun String.isSafe(): Boolean =
        isNotEmpty() &&
            !contains('\\') &&
            !contains(':') &&
            split('/').none { it.isEmpty() || it == "." || it == ".." }

    private fun notABackup(): Nothing = throw BackupException(BackupProblem.NotABackup, name)

    private fun damaged(detail: String): Nothing = throw BackupException(BackupProblem.Damaged, detail)

    companion object {
        fun open(archive: Path): BackupZipReader {
            val zip =
                try {
                    ZipFile(archive.toFile())
                } catch (_: ZipException) {
                    throw BackupException(BackupProblem.NotABackup, archive.fileName.toString())
                }
            var reader: BackupZipReader? = null
            try {
                reader = BackupZipReader(zip, archive.fileName.toString())
                return reader
            } finally {
                // A reader that could not be made cannot close its zip itself.
                if (reader == null) zip.close()
            }
        }
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
