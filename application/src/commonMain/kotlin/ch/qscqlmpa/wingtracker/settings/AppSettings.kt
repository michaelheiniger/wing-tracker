package ch.qscqlmpa.wingtracker.settings

import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import kotlinx.datetime.LocalDate

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val reminderLeadTimes: ReminderLeadTimes = ReminderLeadTimes.DEFAULT,
    // Remembered so the start-up notification appears at most once per day, however often the app is started.
    val lastReminderNotificationOn: LocalDate? = null,
    // Null means "follow the desktop's scaling"; a number is a fixed size in percent.
    val interfaceScalePercent: Int? = null,
    // Remembered so the next import from Syride starts with the same account.
    val syrideUsername: String? = null,
    // Remembered so the next import from a device opens the folder chooser where the logger was last found.
    val deviceFolder: String? = null,
    val trackColoring: TrackColoring = TrackColoring.Flight,
) {
    init {
        require(interfaceScalePercent == null || interfaceScalePercent in MIN_SCALE_PERCENT..MAX_SCALE_PERCENT) {
            "The interface size must be between $MIN_SCALE_PERCENT and $MAX_SCALE_PERCENT percent"
        }
    }

    companion object {
        const val MIN_SCALE_PERCENT = 50
        const val MAX_SCALE_PERCENT = 400
    }
}
