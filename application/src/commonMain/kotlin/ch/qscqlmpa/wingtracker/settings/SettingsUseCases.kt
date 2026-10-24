package ch.qscqlmpa.wingtracker.settings

import ch.qscqlmpa.wingtracker.backup.BackupReminder
import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions

/**
 * Each use case changes one setting on top of what is stored, so saving the theme can never reset the
 * reminders and the other way round.
 */
class SaveThemeMode(
    private val settings: LogbookSettingsRepository,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(mode: ThemeMode) {
        transactions.run(LogbookChange.ThemeModeChanged) { settings.save(settings.load().copy(themeMode = mode)) }
    }
}

class SaveReminderLeadTimes(
    private val settings: LogbookSettingsRepository,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(leadTimes: ReminderLeadTimes) {
        transactions.run(LogbookChange.ReminderLeadTimesChanged) {
            settings.save(settings.load().copy(reminderLeadTimes = leadTimes))
        }
    }
}

class SaveBackupReminder(
    private val settings: DeviceSettingsRepository,
) {
    suspend operator fun invoke(reminder: BackupReminder) {
        settings.save(settings.load().copy(backupReminder = reminder))
    }
}

class SaveInterfaceScale(
    private val settings: DeviceSettingsRepository,
) {
    /** [percent] is null to follow the desktop's scaling. */
    suspend operator fun invoke(percent: Int?) {
        settings.save(settings.load().copy(interfaceScalePercent = percent))
    }
}

class SaveDeviceFolder(
    private val settings: DeviceSettingsRepository,
) {
    suspend operator fun invoke(folder: String) {
        settings.save(settings.load().copy(deviceFolder = folder.trim().ifEmpty { null }))
    }
}

class SaveAirtimeNick(
    private val settings: LogbookSettingsRepository,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(nick: String) {
        transactions.run(LogbookChange.AirtimeNickChanged) {
            settings.save(settings.load().copy(airtimeNick = nick.trim().ifEmpty { null }))
        }
    }
}

class SaveSyrideUsername(
    private val settings: LogbookSettingsRepository,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(username: String) {
        transactions.run(LogbookChange.SyrideUsernameChanged) {
            settings.save(settings.load().copy(syrideUsername = username.trim().ifEmpty { null }))
        }
    }
}
