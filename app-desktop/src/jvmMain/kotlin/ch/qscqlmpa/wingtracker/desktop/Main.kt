package ch.qscqlmpa.wingtracker.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import ch.qscqlmpa.wingtracker.settings.SaveInterfaceScale
import ch.qscqlmpa.wingtracker.settings.SaveThemeMode
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.ui.backup.BackupUseCases
import ch.qscqlmpa.wingtracker.ui.errors.UnexpectedErrorContent
import ch.qscqlmpa.wingtracker.ui.errors.UnexpectedErrors
import ch.qscqlmpa.wingtracker.ui.errors.unexpectedErrorTitle
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
import ch.qscqlmpa.wingtracker.ui.updates.UpdateUseCases
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import javax.imageio.ImageIO

private const val SYSTEM_THEME_POLL_MILLIS = 5_000L
private const val APP_ID = "ch.qscqlmpa.wingtracker"
private const val WINDOW_WIDTH = 1100
private const val WINDOW_HEIGHT = 950
private const val ERROR_WIDTH = 760
private const val ERROR_HEIGHT = 520

private val logger = KotlinLogging.logger {}

fun main() {
    val devMode = DevMode.isOn()
    // The application id names FileKit's own per-user folders and identifies the dialogs to the desktop portal.
    FileKit.init(appId = APP_ID)
    val dataDirectory = DataDirectory.configured(devMode)
    // Enough context to read a pilot's log without asking which version and setup produced it.
    logger.info {
        "Starting Wing Tracker ${BuildInfo.VERSION} (dev mode: $devMode) on ${System.getProperty("os.name")} " +
            "${System.getProperty("os.version")}, Java ${System.getProperty("java.version")}; " +
            "data folder $dataDirectory, log folder ${DesktopLogging.directory(devMode)}"
    }
    val koin = startKoin { modules(appModules(dataDirectory, devMode)) }.koin

    // Loaded before the first frame so the window never flashes the wrong theme or size.
    val settings = runBlocking { koin.get<SettingsRepository>().load() }
    val themePreference = koin.get<ThemePreference>().also { it.mode = settings.themeMode }
    val scalePreference = InterfaceScalePreference(settings.interfaceScalePercent)
    val detectedScale = DesktopScale.detect()
    val jvmScale = jvmScale()
    val initialFactor = DesktopScale.factor(scalePreference.percent, detectedScale, jvmScale)

    val systemTheme = SystemTheme()
    val initialSystemDark = systemTheme.isDark()
    val errorHandling =
        UnexpectedErrorHandling(UnexpectedErrors(), koin.get<PlatformServices>().logLocation).also { it.install() }

    application {
        var systemDark by remember { mutableStateOf(initialSystemDark) }
        LaunchedEffect(Unit) {
            // Supervised, so a failure is shown to the pilot instead of ending the app.
            supervisorScope {
                launch(errorHandling.coroutineExceptionHandler) { koin.get<StartupReminder>()() }
                // Followed live, so switching the desktop between light and dark needs no restart.
                launch(errorHandling.coroutineExceptionHandler) {
                    while (true) {
                        delay(SYSTEM_THEME_POLL_MILLIS)
                        systemDark = withContext(Dispatchers.IO) { systemTheme.isDark() }
                    }
                }
            }
        }
        val themed: @Composable (@Composable () -> Unit) -> Unit = { content ->
            WithInterfaceScale(DesktopScale.factor(scalePreference.percent, detectedScale, jvmScale)) {
                WingTrackerTheme(themePreference.mode, systemDark ?: isSystemInDarkTheme(), content)
            }
        }
        // Outside the rebuilt part, so a rebuilt window opens where and as large as the old one was.
        // The window is sized in the JVM's own units, so it grows with the interface.
        val windowState =
            rememberWindowState(size = DpSize((WINDOW_WIDTH * initialFactor).dp, (WINDOW_HEIGHT * initialFactor).dp))
        key(errorHandling.windowGeneration) {
            @OptIn(ExperimentalComposeUiApi::class)
            CompositionLocalProvider(
                LocalWindowExceptionHandlerFactory provides errorHandling.windowExceptionHandlerFactory,
            ) {
                MainWindow(koin, devMode, windowState, themePreference, scalePreference, themed)
            }
        }
        UnexpectedErrorWindow(
            errorHandling,
            DesktopScale.factor(scalePreference.percent, detectedScale, jvmScale),
            themed,
        )
    }
}

@Composable
private fun ApplicationScope.MainWindow(
    koin: Koin,
    devMode: Boolean,
    state: WindowState,
    themePreference: ThemePreference,
    scalePreference: InterfaceScalePreference,
    themed: @Composable (@Composable () -> Unit) -> Unit,
) {
    Window(
        onCloseRequest = {
            // A delete the pilot could still have undone must not be lost when the window closes.
            runBlocking { koin.get<UndoCoordinator>().flush() }
            exitApplication()
        },
        title = if (devMode) "Wing Tracker (dev)" else "Wing Tracker",
        state = state,
        icon = applicationIcon(),
    ) {
        // Inside the window, so a failure to save is shown to the pilot instead of ending the app.
        LaunchedEffect(themePreference) {
            val saveThemeMode = koin.get<SaveThemeMode>()
            snapshotFlow { themePreference.mode }.drop(1).collect { saveThemeMode(it) }
        }
        LaunchedEffect(scalePreference) {
            val saveInterfaceScale = koin.get<SaveInterfaceScale>()
            snapshotFlow { scalePreference.percent }.drop(1).collect { saveInterfaceScale(it) }
        }
        themed {
            AppShell(
                logbookUseCases = koin.get<LogbookUseCases>(),
                getStatistics = koin.get<GetStatistics>(),
                gearUseCases = koin.get<GearUseCases>(),
                maintenanceUseCases = koin.get<MaintenanceUseCases>(),
                reminders = koin.get<RemindersUseCases>(),
                backups = koin.get<BackupUseCases>(),
                updates = koin.get<UpdateUseCases>(),
                platform = koin.get<PlatformServices>(),
                themePreference = themePreference,
                scalePreference = scalePreference,
                undo = koin.get<UndoCoordinator>(),
                appVersion = BuildInfo.VERSION,
            )
        }
    }
}

/** Its own window, not a dialog of the main one, so it still shows when the main window is what failed. */
@Composable
private fun UnexpectedErrorWindow(
    errorHandling: UnexpectedErrorHandling,
    scale: Float,
    themed: @Composable (@Composable () -> Unit) -> Unit,
) {
    val error = errorHandling.errors.current ?: return
    DialogWindow(
        onCloseRequest = errorHandling::dismiss,
        // Like the main window, sized in the JVM's own units, so it grows with the interface.
        state = rememberDialogState(size = DpSize((ERROR_WIDTH * scale).dp, (ERROR_HEIGHT * scale).dp)),
        title = unexpectedErrorTitle(),
        icon = applicationIcon(),
    ) {
        themed {
            UnexpectedErrorContent(
                error = error,
                onCopy = { errorHandling.copy(error) },
                onOpenLog = errorHandling::openLog,
                onDismiss = errorHandling::dismiss,
            )
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
