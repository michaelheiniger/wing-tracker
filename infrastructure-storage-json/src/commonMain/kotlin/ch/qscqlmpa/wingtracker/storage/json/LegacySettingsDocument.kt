package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.backup.BackupReminder
import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import ch.qscqlmpa.wingtracker.settings.ThemeMode
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * The settings file as written before it was split into logbook and device settings (ADR 0033); read only to
 * migrate it.
 */
@Serializable
internal data class LegacySettingsDocument(
    val schemaVersion: Int = LAST_SCHEMA_VERSION,
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
    val backupReminderEnabled: Boolean = true,
    val backupReminderDays: Int = BackupReminder.DEFAULT_DAYS,
    val cloudStorage: CloudStorageDocument? = null,
) {
    fun toLogbook(): LogbookSettingsDocument =
        LogbookSettingsDocument(
            themeMode = themeMode,
            reminderLeadDays = reminderLeadDays,
            reminderLeadHoursMinutes = reminderLeadHoursMinutes,
            syrideUsername = syrideUsername,
            airtimeNick = airtimeNick,
            cloudStorage = cloudStorage,
        )

    fun toDevice(): DeviceSettingsDocument =
        DeviceSettingsDocument(
            lastReminderNotificationOn = lastReminderNotificationOn,
            interfaceScalePercent = interfaceScalePercent,
            deviceFolder = deviceFolder,
            lastBackupAt = lastBackupAt,
            backupReminderEnabled = backupReminderEnabled,
            backupReminderDays = backupReminderDays,
        )

    companion object {
        const val LAST_SCHEMA_VERSION = 7
    }
}
