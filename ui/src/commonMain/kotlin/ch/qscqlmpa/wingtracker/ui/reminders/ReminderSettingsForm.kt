package ch.qscqlmpa.wingtracker.ui.reminders

import ch.qscqlmpa.wingtracker.backup.BackupReminder
import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import ch.qscqlmpa.wingtracker.ui.formatHoursAndMinutes
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError
import ch.qscqlmpa.wingtracker.ui.parseHoursAndMinutes

data class ReminderSettingsInput(
    val days: String = "",
    val hours: String = "",
    val backupEnabled: Boolean = true,
    val backupDays: String = BackupReminder.DEFAULT_DAYS.toString(),
) {
    companion object {
        fun from(
            leadTimes: ReminderLeadTimes,
            backup: BackupReminder,
        ) = ReminderSettingsInput(
            days = leadTimes.days.toString(),
            hours = formatHoursAndMinutes(leadTimes.hours.inWholeMinutes),
            backupEnabled = backup.enabled,
            backupDays = backup.days.toString(),
        )
    }
}

enum class ReminderSettingsField { Days, Hours, BackupDays }

sealed interface ReminderSettingsResult {
    data class Valid(
        val leadTimes: ReminderLeadTimes,
        val backup: BackupReminder,
    ) : ReminderSettingsResult

    data class Invalid(
        val errors: Map<ReminderSettingsField, FormError>,
    ) : ReminderSettingsResult
}

private val DAYS = Regex("""\d{1,3}""")

fun ReminderSettingsInput.validate(): ReminderSettingsResult {
    val errors = linkedMapOf<ReminderSettingsField, FormError>()
    val parsedDays = parseDays(days)
    if (parsedDays == null) errors[ReminderSettingsField.Days] = FormError.InvalidDays
    val parsedHours = parseHoursAndMinutes(hours)?.takeIf { it.isPositive() }
    if (parsedHours == null) errors[ReminderSettingsField.Hours] = FormError.InvalidHours
    // The days of a reminder turned off do not matter, so they never block saving; the default stands in.
    val parsedBackupDays = parseDays(backupDays) ?: BackupReminder.DEFAULT_DAYS.takeUnless { backupEnabled }
    if (parsedBackupDays == null) errors[ReminderSettingsField.BackupDays] = FormError.InvalidDays
    if (parsedDays == null || parsedHours == null || parsedBackupDays == null) {
        return ReminderSettingsResult.Invalid(errors)
    }
    return ReminderSettingsResult.Valid(
        ReminderLeadTimes(parsedDays, parsedHours),
        BackupReminder(backupEnabled, parsedBackupDays),
    )
}

private fun parseDays(text: String): Int? =
    text
        .trim()
        .takeIf { DAYS.matches(it) }
        ?.toInt()
        ?.takeIf { it > 0 }
