package ch.qscqlmpa.wingtracker.settings

import ch.qscqlmpa.wingtracker.backup.CloudStorageSettings
import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes

/** Settings that belong to the logbook, so every device it is synchronised to shares them (ADR 0033). */
data class LogbookSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val reminderLeadTimes: ReminderLeadTimes = ReminderLeadTimes.DEFAULT,
    // Remembered so the next import from Syride starts with the same account.
    val syrideUsername: String? = null,
    // Remembered so the next export to air-time compares with the same pilot's flights there (ADR 0029).
    val airtimeNick: String? = null,
    // Null until the pilot sets up a bucket to upload backups to (ADR 0032).
    val cloudStorage: CloudStorageSettings? = null,
)
