package ch.qscqlmpa.wingtracker.ui.reminders

import ch.qscqlmpa.wingtracker.reminders.StartNotification
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.notification_due
import ch.qscqlmpa.wingtracker.ui.resources.notification_due_soon
import ch.qscqlmpa.wingtracker.ui.resources.notification_message
import ch.qscqlmpa.wingtracker.ui.resources.notification_planned
import ch.qscqlmpa.wingtracker.ui.resources.notification_title
import org.jetbrains.compose.resources.getString

private const val MAX_NAMES = 3

/** Title and message for the start-up notification, in the user's language. */
suspend fun StartNotification.toText(): Pair<String, String> {
    val counts =
        listOfNotNull(
            dueCount.takeIf { it > 0 }?.let { getString(Res.string.notification_due, it) },
            dueSoonCount.takeIf { it > 0 }?.let { getString(Res.string.notification_due_soon, it) },
            plannedCount.takeIf { it > 0 }?.let { getString(Res.string.notification_planned, it) },
        ).joinToString(", ")
    val shown = names.take(MAX_NAMES).joinToString(", ") + if (names.size > MAX_NAMES) ", …" else ""
    return getString(Res.string.notification_title) to getString(Res.string.notification_message, counts, shown)
}
