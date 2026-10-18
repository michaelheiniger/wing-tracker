package ch.qscqlmpa.wingtracker.backup

import kotlin.time.Instant

/** What went into a backup, so the pilot can see it holds what they expect. */
data class BackupSummary(
    val files: Int,
    val bytes: Long,
)

/** What a backup holds, read from its manifest once every file was checked against it. */
data class BackupContents(
    val createdAt: Instant,
    val appVersion: String,
    val summary: BackupSummary,
)

enum class BackupProblem {
    // A backup inside the data folder would be backed up into the next one, growing each time.
    InsideDataFolder,

    // Not a zip, or a zip without the manifest of a Wing Tracker backup.
    NotABackup,

    // A file is missing, extra or not what the manifest lists: the archive is incomplete or was altered.
    Damaged,

    // Written in an archive format this version does not know; a newer version can restore it.
    NewerFormat,

    // A restore never overwrites files nor mixes them with a backup's, so it needs an empty folder.
    FolderNotEmpty,
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

    /**
     * Reads the manifest of the backup at [archive] and checks every file in it against the manifest.
     * @throws BackupException when it is no backup, or not a complete one.
     */
    suspend fun inspect(archive: String): BackupContents

    /**
     * Unpacks the backup at [archive] into the empty [folder], checking each file as it goes. On failure, nothing
     * unpacked is left behind.
     * @throws BackupException when the folder is not empty or the backup not complete.
     */
    suspend fun restore(
        archive: String,
        folder: String,
    )

    /** A path named [name] in a new folder outside the data folder, for a backup that only passes through. */
    suspend fun temporaryFile(name: String): String

    /** Removes a file from [temporaryFile] and its folder; one already gone is no error. */
    suspend fun discard(path: String)
}
