package ch.qscqlmpa.wingtracker.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ch.qscqlmpa.wingtracker.settings.SaveInterfaceScale
import ch.qscqlmpa.wingtracker.settings.SaveThemeMode
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.ui.flights.LogbookUseCases
import ch.qscqlmpa.wingtracker.ui.gear.GearUseCases
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.MaintenanceUseCases
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.reminders.RemindersUseCases
import ch.qscqlmpa.wingtracker.ui.shell.AppShell
import ch.qscqlmpa.wingtracker.ui.theme.InterfaceScalePreference
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
import ch.qscqlmpa.wingtracker.ui.theme.WingTrackerTheme
import ch.qscqlmpa.wingtracker.ui.undo.UndoCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import javax.imageio.ImageIO

private const val SYSTEM_THEME_POLL_MILLIS = 5_000L
private const val WINDOW_WIDTH = 1100
private const val WINDOW_HEIGHT = 950

fun main() {
    val devMode = System.getProperty("wingtracker.dev").toBoolean()
    val koin = startKoin { modules(appModules(DataDirectory.resolve(devMode))) }.koin

    val saveThemeMode = koin.get<SaveThemeMode>()
    val saveInterfaceScale = koin.get<SaveInterfaceScale>()
    // Loaded before the first frame so the window never flashes the wrong theme or size.
    val settings = runBlocking { koin.get<SettingsRepository>().load() }
    val themePreference = koin.get<ThemePreference>().also { it.mode = settings.themeMode }
    val scalePreference = InterfaceScalePreference(settings.interfaceScalePercent)
    val detectedScale = DesktopScale.detect()
    val jvmScale = jvmScale()
    val initialFactor = DesktopScale.factor(scalePreference.percent, detectedScale, jvmScale)

    val systemTheme = SystemTheme()
    val initialSystemDark = systemTheme.isDark()

    application {
        // Followed live, so switching the desktop between light and dark needs no restart.
        var systemDark by remember { mutableStateOf(initialSystemDark) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(SYSTEM_THEME_POLL_MILLIS)
                systemDark = withContext(Dispatchers.IO) { systemTheme.isDark() }
            }
        }
        LaunchedEffect(themePreference) {
            snapshotFlow { themePreference.mode }.drop(1).collect { saveThemeMode(it) }
        }
        LaunchedEffect(scalePreference) {
            snapshotFlow { scalePreference.percent }.drop(1).collect { saveInterfaceScale(it) }
        }
        LaunchedEffect(Unit) { koin.get<StartupReminder>()() }
        Window(
            onCloseRequest = {
                // A delete the pilot could still have undone must not be lost when the window closes.
                runBlocking { koin.get<UndoCoordinator>().flush() }
                exitApplication()
            },
            title = if (devMode) "Wing Tracker (dev)" else "Wing Tracker",
            // The window is sized in the JVM's own units, so it grows with the interface.
            state =
                rememberWindowState(
                    size = DpSize((WINDOW_WIDTH * initialFactor).dp, (WINDOW_HEIGHT * initialFactor).dp),
                ),
            icon = applicationIcon(),
        ) {
            WithInterfaceScale(DesktopScale.factor(scalePreference.percent, detectedScale, jvmScale)) {
                WingTrackerTheme(themePreference.mode, systemDark ?: isSystemInDarkTheme()) {
                    AppShell(
                        logbookUseCases = koin.get<LogbookUseCases>(),
                        getStatistics = koin.get<GetStatistics>(),
                        gearUseCases = koin.get<GearUseCases>(),
                        maintenanceUseCases = koin.get<MaintenanceUseCases>(),
                        reminders = koin.get<RemindersUseCases>(),
                        platform = koin.get<PlatformServices>(),
                        themePreference = themePreference,
                        scalePreference = scalePreference,
                        undo = koin.get<UndoCoordinator>(),
                    )
                }
            }
        }
    }
}

/** The scale the JVM applied to this screen itself; 1.0 when it applied none or there is no screen. */
private fun jvmScale(): Double =
    try {
        GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
    } catch (_: HeadlessException) {
        1.0
    }

@Composable
private fun WithInterfaceScale(
    factor: Float,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * factor, base.fontScale), content = content)
}

/** The window icon, generated by design/generate-icon.py; null leaves the platform default if it is missing. */
private fun applicationIcon(): BitmapPainter? =
    object {}.javaClass.getResource("/icon.png")?.let { BitmapPainter(ImageIO.read(it).toComposeImageBitmap()) }
