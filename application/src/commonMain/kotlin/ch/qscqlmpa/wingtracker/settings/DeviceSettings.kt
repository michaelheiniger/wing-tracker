package ch.qscqlmpa.wingtracker.settings

import ch.qscqlmpa.wingtracker.backup.BackupReminder
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Settings that belong to this device, never synchronised with the logbook (ADR 0033). */
data class DeviceSettings(
    // Remembered so the start-up notification appears at most once per day, however often the app is started.
    val lastReminderNotificationOn: LocalDate? = null,
    // Null means "follow the desktop's scaling"; a number is a fixed size in percent.
    val interfaceScalePercent: Int? = null,
    // Remembered so the next import from a device opens the folder chooser where the logger was last found.
    val deviceFolder: String? = null,
    // Remembered so the pilot can see how old this device's last backup is (ADR 0031).
    val lastBackupAt: Instant? = null,
    val backupReminder: BackupReminder = BackupReminder(),
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
