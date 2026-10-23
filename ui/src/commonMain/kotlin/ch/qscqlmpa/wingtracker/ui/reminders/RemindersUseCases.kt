package ch.qscqlmpa.wingtracker.ui.reminders

import ch.qscqlmpa.wingtracker.reminders.GetAttention
import ch.qscqlmpa.wingtracker.reminders.GetCalendarEntries
import ch.qscqlmpa.wingtracker.settings.GetDeviceSettings
import ch.qscqlmpa.wingtracker.settings.GetLogbookSettings
import ch.qscqlmpa.wingtracker.settings.SaveBackupReminder
import ch.qscqlmpa.wingtracker.settings.SaveReminderLeadTimes

/** Use cases the reminder features need; passed in so this module never sees the DI framework. */
class RemindersUseCases(
    val getAttention: GetAttention,
    val getLogbookSettings: GetLogbookSettings,
    val getDeviceSettings: GetDeviceSettings,
    val saveReminderLeadTimes: SaveReminderLeadTimes,
    val getCalendarEntries: GetCalendarEntries,
    val saveBackupReminder: SaveBackupReminder,
)
