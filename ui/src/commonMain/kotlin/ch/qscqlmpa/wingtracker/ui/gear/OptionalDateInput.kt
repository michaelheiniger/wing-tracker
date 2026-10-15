package ch.qscqlmpa.wingtracker.ui.gear

import ch.qscqlmpa.wingtracker.ui.parseDate
import kotlinx.datetime.LocalDate

/** An empty optional date is no date; anything else must be a real day, or [onInvalid] reports it. */
fun parseOptionalDate(
    text: String,
    onInvalid: () -> Unit,
): LocalDate? {
    if (text.isBlank()) return null
    return parseDate(text) ?: run {
        onInvalid()
        null
    }
}
