package ch.qscqlmpa.wingtracker.ui.reminders

import kotlinx.datetime.LocalDate

/** One all-day event, already worded in the user's language. */
data class IcsEvent(
    val uid: String,
    val date: LocalDate,
    val summary: String,
    val description: String? = null,
)
