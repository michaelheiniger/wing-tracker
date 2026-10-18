package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.backup.BackupReminder
import ch.qscqlmpa.wingtracker.backup.CloudProvider
import ch.qscqlmpa.wingtracker.backup.CloudStorageSettings
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
    // Also schema 7: the bucket backups are uploaded to; its secret key is kept by the OS (ADR 0032).
    val cloudStorage: CloudStorageDocument? = null,
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
            cloudStorage = cloudStorage?.toDomain(),
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
                cloudStorage = settings.cloudStorage?.let(CloudStorageDocument::from),
            )
    }
}

@Serializable
internal data class CloudStorageDocument(
    val provider: String,
    val region: String,
    val endpoint: String? = null,
    val bucket: String,
    val accessKeyId: String,
    val keepLast: Int? = null,
) {
    fun toDomain() =
        CloudStorageSettings(CloudProvider.valueOf(provider), region, endpoint, bucket, accessKeyId, keepLast)

    companion object {
        fun from(cloud: CloudStorageSettings) =
            CloudStorageDocument(
                cloud.provider.name,
                cloud.region,
                cloud.endpoint,
                cloud.bucket,
                cloud.accessKeyId,
                cloud.keepLast,
            )
    }
}
