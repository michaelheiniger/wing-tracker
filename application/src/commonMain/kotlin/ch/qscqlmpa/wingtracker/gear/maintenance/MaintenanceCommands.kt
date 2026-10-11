package ch.qscqlmpa.wingtracker.gear.maintenance

import kotlinx.datetime.LocalDate

/** A file picked by the user, not yet stored. */
class NewAttachment(
    val fileName: String,
    val bytes: ByteArray,
)

data class CheckDetails(
    val date: LocalDate,
    val type: CheckType,
    val company: String? = null,
    val notes: String? = null,
    val restartsInterval: Boolean = type != CheckType.Other,
)

data class PlannedCheckDetails(
    val date: LocalDate,
    val type: CheckType,
    val company: String? = null,
    val notes: String? = null,
)

class CheckDateInFutureException : IllegalArgumentException("A performed check cannot be dated in the future")

class PlannedDateInPastException : IllegalArgumentException("A planned check cannot be dated in the past")
