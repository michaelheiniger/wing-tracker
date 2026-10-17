package ch.qscqlmpa.wingtracker.github

import ch.qscqlmpa.wingtracker.updates.DownloadedFile
import ch.qscqlmpa.wingtracker.updates.ReleaseAsset
import ch.qscqlmpa.wingtracker.updates.ReleaseDownloads
import ch.qscqlmpa.wingtracker.updates.ReleaseProblem
import ch.qscqlmpa.wingtracker.updates.ReleaseSourceException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream

private const val BUFFER_BYTES = 64 * 1024

private val logger = KotlinLogging.logger {}

/**
 * Downloads release files into [directory], a cache folder of this installation that holds only the latest download:
 * an installer is large, and an older one is of no use once a newer one is fetched (ADR 0024).
 */
class HttpReleaseDownloads(
    private val directory: Path,
    private val userAgent: String,
    private val get: suspend (url: String, userAgent: String) -> HttpAnswer = ::httpGet,
    private val open: suspend (url: String, userAgent: String) -> HttpStream = ::httpOpen,
) : ReleaseDownloads {
    override suspend fun readText(asset: ReleaseAsset): String {
        val answer = get(asset.downloadUrl, userAgent)
        checkAnswered(answer.status, "Reading ${asset.name}")
        return answer.body
    }

    override suspend fun download(
        asset: ReleaseAsset,
        onProgress: (received: Long, total: Long?) -> Unit,
    ): DownloadedFile {
        val target = directory.resolve(safeName(asset.name))
        val stream = open(asset.downloadUrl, userAgent)
        return withContext(Dispatchers.IO) {
            stream.body.use { body ->
                checkAnswered(stream.status, "Downloading ${asset.name}")
                clearEarlierDownloads()
                // Written under a temporary name, so a cut-off download never looks like a finished one.
                val partial = directory.resolve("${target.fileName}.part")
                val digest = MessageDigest.getInstance("SHA-256")
                try {
                    var received = 0L
                    partial.outputStream().use { out ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            ensureActive()
                            val count = body.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            received += count
                            onProgress(received, stream.length)
                        }
                    }
                    Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
                    logger.info { "Downloaded ${asset.name} ($received bytes) to $target" }
                } catch (e: IOException) {
                    logger.warn(e) { "Downloading ${asset.name} failed" }
                    throw ReleaseSourceException(ReleaseProblem.Unreachable, e.message, e)
                } finally {
                    partial.deleteIfExists()
                }
                DownloadedFile(target.toString(), digest.digest().toHex())
            }
        }
    }

    override suspend fun discard(file: DownloadedFile) {
        withContext(Dispatchers.IO) { Path.of(file.path).deleteIfExists() }
        logger.warn { "Discarded ${file.path}, whose checksum is not the published one" }
    }

    private fun clearEarlierDownloads() {
        directory.createDirectories()
        directory.listDirectoryEntries().filter { it.isRegularFile() }.forEach { it.deleteIfExists() }
    }

    // The name comes from the network, so it must not be able to point outside the folder.
    private fun safeName(name: String): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
        if (cleaned.isBlank() || cleaned == "." || cleaned == "..") {
            throw ReleaseSourceException(ReleaseProblem.Unexpected, "Unusable file name '$name'")
        }
        return cleaned
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
