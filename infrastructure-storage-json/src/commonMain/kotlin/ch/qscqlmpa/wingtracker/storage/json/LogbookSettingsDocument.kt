package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.backup.CloudProvider
import ch.qscqlmpa.wingtracker.backup.CloudStorageSettings
import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import ch.qscqlmpa.wingtracker.settings.LogbookSettings
import ch.qscqlmpa.wingtracker.settings.ThemeMode
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes

/** Continues the numbering of the settings file it was split from, so older versions see it as newer data. */
@Serializable
internal data class LogbookSettingsDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val themeMode: String = ThemeMode.System.name,
    val reminderLeadDays: Int = ReminderLeadTimes.DEFAULT.days,
    val reminderLeadHoursMinutes: Long = ReminderLeadTimes.DEFAULT.hours.inWholeMinutes,
    val syrideUsername: String? = null,
    val airtimeNick: String? = null,
    // The secret key is kept by the OS (ADR 0032).
    val cloudStorage: CloudStorageDocument? = null,
) {
    fun toDomain(): LogbookSettings =
        LogbookSettings(
            themeMode = ThemeMode.valueOf(themeMode),
            reminderLeadTimes = ReminderLeadTimes(reminderLeadDays, reminderLeadHoursMinutes.minutes),
            syrideUsername = syrideUsername,
            airtimeNick = airtimeNick,
            cloudStorage = cloudStorage?.toDomain(),
        )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 8

        fun from(settings: LogbookSettings) =
            LogbookSettingsDocument(
                themeMode = settings.themeMode.name,
                reminderLeadDays = settings.reminderLeadTimes.days,
                reminderLeadHoursMinutes = settings.reminderLeadTimes.hours.inWholeMinutes,
                syrideUsername = settings.syrideUsername,
                airtimeNick = settings.airtimeNick,
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
