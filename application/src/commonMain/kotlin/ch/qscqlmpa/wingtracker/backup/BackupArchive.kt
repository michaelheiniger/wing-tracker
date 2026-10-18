package ch.qscqlmpa.wingtracker.backup

import kotlin.time.Instant

/** What went into a backup, so the pilot can see it holds what they expect. */
data class BackupSummary(
    val files: Int,
    val bytes: Long,
)

enum class BackupProblem {
    // A backup inside the data folder would be backed up into the next one, growing each time.
    InsideDataFolder,
}

class BackupException(
    val problem: BackupProblem,
    detail: String? = null,
) : Exception(detail ?: problem.name)

/** Archives the stored data (ADR 0031); only the storage adapter knows what the stored data consists of. */
interface BackupArchive {
    /**
     * Writes every stored file, and a manifest naming [appVersion] and [createdAt], to an archive at [destination].
     * The archive appears there only once it is complete.
     * @throws BackupException when the archive cannot be made there.
     */
    suspend fun create(
        destination: String,
        appVersion: String,
        createdAt: Instant,
    ): BackupSummary
}
