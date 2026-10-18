package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.backup.BackupReminder
import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.ThemeMode
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Serializable
internal data class SettingsDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val themeMode: String = ThemeMode.System.name,
    // Added in schema 2; a file written before it simply uses the defaults.
    val reminderLeadDays: Int = ReminderLeadTimes.DEFAULT.days,
    val reminderLeadHoursMinutes: Long = ReminderLeadTimes.DEFAULT.hours.inWholeMinutes,
    val lastReminderNotificationOn: LocalDate? = null,
    // Added in schema 3; null follows the desktop.
    val interfaceScalePercent: Int? = null,
    // Added in schema 4: the Syride account whose flights are imported.
    val syrideUsername: String? = null,
    // Added in schema 5: where the flight tracks of a device were last imported from.
    val deviceFolder: String? = null,
    // Added in schema 6: the air-time pilot whose flights an export compares with.
    val airtimeNick: String? = null,
    // Added in schema 7: when the last backup was made, as ISO-8601 text like the flights' instants.
    val lastBackupAt: String? = null,
    // Also schema 7, which no release has used yet: when to remind the pilot to back up.
    val backupReminderEnabled: Boolean = true,
    val backupReminderDays: Int = BackupReminder.DEFAULT_DAYS,
) {
    fun toDomain(): AppSettings =
        AppSettings(
            themeMode = ThemeMode.valueOf(themeMode),
            reminderLeadTimes = ReminderLeadTimes(reminderLeadDays, reminderLeadHoursMinutes.minutes),
            lastReminderNotificationOn = lastReminderNotificationOn,
            interfaceScalePercent = interfaceScalePercent,
            syrideUsername = syrideUsername,
            deviceFolder = deviceFolder,
            airtimeNick = airtimeNick,
            lastBackupAt = lastBackupAt?.let(Instant::parse),
            backupReminder = BackupReminder(backupReminderEnabled, backupReminderDays),
        )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 7

        fun from(settings: AppSettings) =
            SettingsDocument(
                themeMode = settings.themeMode.name,
                reminderLeadDays = settings.reminderLeadTimes.days,
                reminderLeadHoursMinutes = settings.reminderLeadTimes.hours.inWholeMinutes,
                lastReminderNotificationOn = settings.lastReminderNotificationOn,
                interfaceScalePercent = settings.interfaceScalePercent,
                syrideUsername = settings.syrideUsername,
                deviceFolder = settings.deviceFolder,
                airtimeNick = settings.airtimeNick,
                lastBackupAt = settings.lastBackupAt?.toString(),
                backupReminderEnabled = settings.backupReminder.enabled,
                backupReminderDays = settings.backupReminder.days,
            )
    }
}
