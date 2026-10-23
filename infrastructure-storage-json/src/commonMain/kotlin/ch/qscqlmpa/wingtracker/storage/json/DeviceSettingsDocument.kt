package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.backup.BackupReminder
import ch.qscqlmpa.wingtracker.settings.DeviceSettings
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
internal data class DeviceSettingsDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val lastReminderNotificationOn: LocalDate? = null,
    // Null follows the desktop.
    val interfaceScalePercent: Int? = null,
    val deviceFolder: String? = null,
    // ISO-8601 text, like the flights' instants.
    val lastBackupAt: String? = null,
    val backupReminderEnabled: Boolean = true,
    val backupReminderDays: Int = BackupReminder.DEFAULT_DAYS,
) {
    fun toDomain(): DeviceSettings =
        DeviceSettings(
            lastReminderNotificationOn = lastReminderNotificationOn,
            interfaceScalePercent = interfaceScalePercent,
            deviceFolder = deviceFolder,
            lastBackupAt = lastBackupAt?.let(Instant::parse),
            backupReminder = BackupReminder(backupReminderEnabled, backupReminderDays),
        )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun from(settings: DeviceSettings) =
            DeviceSettingsDocument(
                lastReminderNotificationOn = settings.lastReminderNotificationOn,
                interfaceScalePercent = settings.interfaceScalePercent,
                deviceFolder = settings.deviceFolder,
                lastBackupAt = settings.lastBackupAt?.toString(),
                backupReminderEnabled = settings.backupReminder.enabled,
                backupReminderDays = settings.backupReminder.days,
            )
    }
}
