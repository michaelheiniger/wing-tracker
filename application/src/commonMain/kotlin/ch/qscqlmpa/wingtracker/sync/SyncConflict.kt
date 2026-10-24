package ch.qscqlmpa.wingtracker.sync

import kotlin.time.Instant

/** A record changed on this device and on another since the last sync, as the pilot compares the two versions. */
data class SyncConflict(
    val record: SyncedRecord,
    val here: ConflictSide,
    val there: ConflictSide,
    // Only what differs, as stored; empty for a file that is not a document, such as an attachment.
    val differences: List<FieldDifference>,
)

/** One side of a conflict: the last change to the record there, and whether that side deleted it. */
data class ConflictSide(
    val change: RecordChange?,
    val deleted: Boolean,
)

data class RecordChange(
    val author: String,
    val at: Instant,
    // As the history words it (ADR 0033); not translated.
    val description: String,
)

/** A field as stored, e.g. `durationMinutes`; a value is null where that side has no such field. */
data class FieldDifference(
    val field: String,
    val here: String?,
    val there: String?,
)

enum class ConflictChoice { Here, There }
