package ch.qscqlmpa.wingtracker.settings

import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes

/**
 * Each use case changes one setting on top of what is stored, so saving the theme can never reset the
 * reminders and the other way round.
 */
class SaveThemeMode(
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(mode: ThemeMode) {
        settings.save(settings.load().copy(themeMode = mode))
    }
}

class SaveReminderLeadTimes(
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(leadTimes: ReminderLeadTimes) {
        settings.save(settings.load().copy(reminderLeadTimes = leadTimes))
    }
}
