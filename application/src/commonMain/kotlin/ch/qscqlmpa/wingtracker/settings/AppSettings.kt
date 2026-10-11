package ch.qscqlmpa.wingtracker.settings

import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import kotlinx.datetime.LocalDate

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val reminderLeadTimes: ReminderLeadTimes = ReminderLeadTimes.DEFAULT,
    // Remembered so the start-up notification appears at most once per day, however often the app is started.
    val lastReminderNotificationOn: LocalDate? = null,
)
