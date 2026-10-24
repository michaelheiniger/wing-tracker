package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.backup.BackupReminder
import ch.qscqlmpa.wingtracker.settings.DeviceSettings
import ch.qscqlmpa.wingtracker.sync.SyncSettings
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
    // Also schema 1, which no release has used yet: where the logbook is synchronised to (ADR 0033).
    val sync: SyncSettingsDocument? = null,
    val lastSyncedAt: String? = null,
) {
    fun toDomain(): DeviceSettings =
        DeviceSettings(
            lastReminderNotificationOn = lastReminderNotificationOn,
            interfaceScalePercent = interfaceScalePercent,
            deviceFolder = deviceFolder,
            lastBackupAt = lastBackupAt?.let(Instant::parse),
            backupReminder = BackupReminder(backupReminderEnabled, backupReminderDays),
            sync = sync?.toDomain(),
            lastSyncedAt = lastSyncedAt?.let(Instant::parse),
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
                sync = settings.sync?.let(SyncSettingsDocument::from),
                lastSyncedAt = settings.lastSyncedAt?.toString(),
            )
    }
}

@Serializable
internal data class SyncSettingsDocument(
    val repository: String,
    val username: String? = null,
    val authorName: String,
    val authorEmail: String,
) {
    fun toDomain() = SyncSettings(repository, username, authorName, authorEmail)

    companion object {
        fun from(settings: SyncSettings) =
            SyncSettingsDocument(settings.repository, settings.username, settings.authorName, settings.authorEmail)
    }
}
