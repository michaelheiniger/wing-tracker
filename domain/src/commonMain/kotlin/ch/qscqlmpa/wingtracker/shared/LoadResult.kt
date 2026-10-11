package ch.qscqlmpa.wingtracker.shared

/**
 * Outcome of loading many records. One unreadable record must not hide all the others,
 * so problems are reported next to the records that did load.
 */
data class LoadResult<T>(
    val items: List<T>,
    val unreadable: List<UnreadableRecord> = emptyList(),
)

/** [quarantined] tells whether the file was moved aside, so the user knows where to look. */
data class UnreadableRecord(
    val name: String,
    val reason: String,
    val quarantined: Boolean,
)
