package ch.qscqlmpa.wingtracker.updates

/** A file downloaded to this computer, with the SHA-256 checksum of what actually arrived. */
data class DownloadedFile(
    val path: String,
    val sha256: String,
)

/** Fetches the files of a release (ADR 0024). */
interface ReleaseDownloads {
    /** @throws ReleaseSourceException when the file cannot be had. */
    suspend fun readText(asset: ReleaseAsset): String

    /**
     * Saves [asset] to a local folder, reporting the bytes received and the total, if known, as it goes.
     * @throws ReleaseSourceException when the file cannot be had.
     */
    suspend fun download(
        asset: ReleaseAsset,
        onProgress: (received: Long, total: Long?) -> Unit,
    ): DownloadedFile

    /** Removes a file that must not be installed. */
    suspend fun discard(file: DownloadedFile)
}

enum class DownloadProblem {
    // The release publishes no checksum for its installer, so a broken download could not be told apart.
    NoChecksum,

    // What arrived is not what was published: the download is corrupt, incomplete or altered.
    ChecksumMismatch,
    NoConnection,
    Unreachable,
    Refused,
    Unexpected,
}

sealed interface DownloadResult {
    /** The installer at [path] is exactly the one the release published. */
    data class Verified(
        val path: String,
    ) : DownloadResult

    data class Failed(
        val problem: DownloadProblem,
        val detail: String?,
    ) : DownloadResult
}

/** Downloads the installer of an update and keeps it only if its checksum is the published one (ADR 0024). */
class DownloadUpdate(
    private val downloads: ReleaseDownloads,
) {
    suspend operator fun invoke(
        update: AvailableUpdate,
        onProgress: (received: Long, total: Long?) -> Unit,
    ): DownloadResult {
        val checksumFile = update.latest.checksumFile ?: return DownloadResult.Failed(DownloadProblem.NoChecksum, null)
        val installer = update.installer
        return try {
            val checksums = Checksums.parse(downloads.readText(checksumFile))
            // Checked before downloading, so a release that does not list its installer costs no download.
            if (checksums.of(installer.name) == null) {
                return DownloadResult.Failed(DownloadProblem.NoChecksum, installer.name)
            }
            val file = downloads.download(installer, onProgress)
            if (checksums.matches(installer.name, file.sha256)) {
                DownloadResult.Verified(file.path)
            } else {
                downloads.discard(file)
                DownloadResult.Failed(DownloadProblem.ChecksumMismatch, installer.name)
            }
        } catch (e: ReleaseSourceException) {
            DownloadResult.Failed(e.problem.asDownloadProblem(), e.message)
        }
    }
}

private fun ReleaseProblem.asDownloadProblem(): DownloadProblem =
    when (this) {
        ReleaseProblem.NoConnection -> DownloadProblem.NoConnection
        ReleaseProblem.Unreachable -> DownloadProblem.Unreachable
        ReleaseProblem.Refused -> DownloadProblem.Refused
        ReleaseProblem.Unexpected -> DownloadProblem.Unexpected
    }
