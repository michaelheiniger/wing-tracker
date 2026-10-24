package ch.qscqlmpa.wingtracker.git

import ch.qscqlmpa.wingtracker.sync.FieldDifference
import ch.qscqlmpa.wingtracker.sync.RecordSummary
import ch.qscqlmpa.wingtracker.sync.SyncedRecord

/**
 * What this adapter needs to know about the folder it synchronises. The storage adapter knows the layout; the
 * composition root hands it over, so neither adapter depends on the other (ADR 0033). Paths are relative to the
 * folder, with `/` between their parts, as Git writes them.
 */
interface SyncedFolderLayout {
    /** Files that stay on this device, in `.gitignore` syntax. */
    val deviceOnly: List<String>

    /** Whether the file at [path] is missing because it was quarantined (ADR 0015), so it must not count as deleted. */
    fun isQuarantined(path: String): Boolean

    fun recordAt(path: String): SyncedRecord

    /** What names the record stored at [path] for the pilot; null when the file does not say. */
    fun summary(
        path: String,
        version: ByteArray,
    ): RecordSummary?

    /** The fields that differ between two versions of the file at [path]; a version is null where it was deleted. */
    fun differences(
        path: String,
        here: ByteArray?,
        there: ByteArray?,
    ): List<FieldDifference>
}
