package ch.qscqlmpa.wingtracker.storage.json

import kotlinx.serialization.Serializable

/** Lists what a backup holds, so a restore can tell a complete archive from a broken or altered one (ADR 0031). */
@Serializable
internal data class BackupManifestDocument(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val appVersion: String,
    // ISO-8601 text keeps the file readable and independent of the serialization library's Instant support.
    val createdAt: String,
    val files: List<BackupEntryDocument>,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1

        // Named after the application, so it cannot be mistaken for a file of the data folder.
        const val ENTRY_NAME = "wing-tracker-backup.json"
    }
}

/** One file of the data folder; [path] is relative to it, with `/` between folders whatever the system. */
@Serializable
internal data class BackupEntryDocument(
    val path: String,
    val size: Long,
    val sha256: String,
)
