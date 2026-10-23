package ch.qscqlmpa.wingtracker.reminders

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.gear.maintenance.DueState
import ch.qscqlmpa.wingtracker.settings.DeviceSettingsRepository

/** What the start-up notification should say; the wording itself belongs to the UI layer (ADR 0014). */
data class StartNotification(
    val dueCount: Int,
    val dueSoonCount: Int,
    val plannedCount: Int,
    val names: List<String>,
    val backupDue: Boolean = false,
)

/**
 * Decides whether the pilot should be notified on start: only when something needs attention and not yet today.
 * Showing is a separate step, so a notification that could not be shown is tried again on the next start.
 */
class GetStartNotification(
    private val attention: GetAttention,
    private val settings: DeviceSettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(): StartNotification? {
        if (settings.load().lastReminderNotificationOn == clock.today()) return null
        val overview = attention()
        if (overview.isEmpty) return null
        return StartNotification(
            dueCount = overview.dueCount,
            dueSoonCount = overview.dueSoonCount,
            plannedCount = overview.items.count { it.state == DueState.Ok },
            names = overview.items.map { it.name },
            backupDue = overview.backup != null,
        )
    }
}

class MarkStartNotificationShown(
    private val settings: DeviceSettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke() {
        settings.save(settings.load().copy(lastReminderNotificationOn = clock.today()))
    }
}
