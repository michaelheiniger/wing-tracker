package ch.qscqlmpa.wingtracker.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ch.qscqlmpa.wingtracker.settings.SaveThemeMode
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.ui.flights.LogbookUseCases
import ch.qscqlmpa.wingtracker.ui.gear.GearUseCases
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.MaintenanceUseCases
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.reminders.RemindersUseCases
import ch.qscqlmpa.wingtracker.ui.shell.AppShell
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
import ch.qscqlmpa.wingtracker.ui.theme.WingTrackerTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin

fun main() {
    val devMode = System.getProperty("wingtracker.dev").toBoolean()
    val koin = startKoin { modules(appModules(DataDirectory.resolve(devMode))) }.koin

    val settingsRepository = koin.get<SettingsRepository>()
    val saveThemeMode = koin.get<SaveThemeMode>()
    val themePreference = koin.get<ThemePreference>()
    // Loaded before the first frame so the window never flashes the wrong theme.
    themePreference.mode = runBlocking { settingsRepository.load() }.themeMode

    application {
        LaunchedEffect(themePreference) {
            snapshotFlow { themePreference.mode }
                .drop(1)
                .collect { saveThemeMode(it) }
        }
        LaunchedEffect(Unit) { koin.get<StartupReminder>()() }
        Window(
            onCloseRequest = ::exitApplication,
            title = if (devMode) "Wing Tracker (dev)" else "Wing Tracker",
            state = rememberWindowState(size = DpSize(1100.dp, 950.dp)),
        ) {
            WingTrackerTheme(themePreference.mode) {
                AppShell(
                    logbookUseCases = koin.get<LogbookUseCases>(),
                    getStatistics = koin.get<GetStatistics>(),
                    gearUseCases = koin.get<GearUseCases>(),
                    maintenanceUseCases = koin.get<MaintenanceUseCases>(),
                    reminders = koin.get<RemindersUseCases>(),
                    platform = koin.get<PlatformServices>(),
                    themePreference = themePreference,
                )
            }
        }
    }
}
