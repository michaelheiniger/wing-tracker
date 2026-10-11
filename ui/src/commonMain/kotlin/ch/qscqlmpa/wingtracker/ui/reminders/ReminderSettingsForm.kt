package ch.qscqlmpa.wingtracker.ui.reminders

import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import ch.qscqlmpa.wingtracker.ui.formatHoursAndMinutes
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError
import ch.qscqlmpa.wingtracker.ui.parseHoursAndMinutes

data class ReminderSettingsInput(
    val days: String = "",
    val hours: String = "",
) {
    companion object {
        fun from(leadTimes: ReminderLeadTimes) =
            ReminderSettingsInput(
                days = leadTimes.days.toString(),
                hours = formatHoursAndMinutes(leadTimes.hours.inWholeMinutes),
            )
    }
}

enum class ReminderSettingsField { Days, Hours }

sealed interface ReminderSettingsResult {
    data class Valid(
        val leadTimes: ReminderLeadTimes,
    ) : ReminderSettingsResult

    data class Invalid(
        val errors: Map<ReminderSettingsField, FormError>,
    ) : ReminderSettingsResult
}

private val DAYS = Regex("""\d{1,3}""")

fun ReminderSettingsInput.validate(): ReminderSettingsResult {
    val errors = linkedMapOf<ReminderSettingsField, FormError>()
    val parsedDays =
        days
            .trim()
            .takeIf { DAYS.matches(it) }
            ?.toInt()
            ?.takeIf { it > 0 }
    if (parsedDays == null) errors[ReminderSettingsField.Days] = FormError.InvalidDays
    val parsedHours = parseHoursAndMinutes(hours)?.takeIf { it.isPositive() }
    if (parsedHours == null) errors[ReminderSettingsField.Hours] = FormError.InvalidHours
    if (parsedDays == null || parsedHours == null) return ReminderSettingsResult.Invalid(errors)
    return ReminderSettingsResult.Valid(ReminderLeadTimes(parsedDays, parsedHours))
}
