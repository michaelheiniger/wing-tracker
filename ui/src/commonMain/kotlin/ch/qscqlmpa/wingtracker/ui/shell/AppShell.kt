package ch.qscqlmpa.wingtracker.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.flights.FlightFilter
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.ui.backup.BackupPresenter
import ch.qscqlmpa.wingtracker.ui.backup.BackupUseCases
import ch.qscqlmpa.wingtracker.ui.backup.CloudRestoreUseCases
import ch.qscqlmpa.wingtracker.ui.backup.CloudSecret
import ch.qscqlmpa.wingtracker.ui.backup.CloudStoragePresenter
import ch.qscqlmpa.wingtracker.ui.backup.CloudStorageUseCases
import ch.qscqlmpa.wingtracker.ui.backup.RestorePresenter
import ch.qscqlmpa.wingtracker.ui.backup.RestoreTitles
import ch.qscqlmpa.wingtracker.ui.components.FileNamesBanner
import ch.qscqlmpa.wingtracker.ui.flights.LogbookRoute
import ch.qscqlmpa.wingtracker.ui.flights.LogbookSearch
import ch.qscqlmpa.wingtracker.ui.flights.LogbookUseCases
import ch.qscqlmpa.wingtracker.ui.gear.GearRoute
import ch.qscqlmpa.wingtracker.ui.gear.GearUseCases
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.MaintenanceUseCases
import ch.qscqlmpa.wingtracker.ui.icons.BarChartIcon
import ch.qscqlmpa.wingtracker.ui.icons.UpdateIcon
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.reminders.AttentionPanel
import ch.qscqlmpa.wingtracker.ui.reminders.AttentionPresenter
import ch.qscqlmpa.wingtracker.ui.reminders.ReminderSettingsPresenter
import ch.qscqlmpa.wingtracker.ui.reminders.RemindersUseCases
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.about_version
import ch.qscqlmpa.wingtracker.ui.resources.attention_badge
import ch.qscqlmpa.wingtracker.ui.resources.backup_save_title
import ch.qscqlmpa.wingtracker.ui.resources.nav_gear
import ch.qscqlmpa.wingtracker.ui.resources.nav_logbook
import ch.qscqlmpa.wingtracker.ui.resources.nav_settings
import ch.qscqlmpa.wingtracker.ui.resources.nav_statistics
import ch.qscqlmpa.wingtracker.ui.resources.restore_folder_title
import ch.qscqlmpa.wingtracker.ui.resources.restore_pick_title
import ch.qscqlmpa.wingtracker.ui.resources.settings_data_choose_title
import ch.qscqlmpa.wingtracker.ui.resources.update_rail_badge
import ch.qscqlmpa.wingtracker.ui.settings.DataLocationPresenter
import ch.qscqlmpa.wingtracker.ui.settings.LogLocationPresenter
import ch.qscqlmpa.wingtracker.ui.settings.SettingsCategory
import ch.qscqlmpa.wingtracker.ui.settings.SettingsScreen
import ch.qscqlmpa.wingtracker.ui.statistics.StatisticsRoute
import ch.qscqlmpa.wingtracker.ui.theme.InterfaceScalePreference
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
import ch.qscqlmpa.wingtracker.ui.undo.LocalUndo
import ch.qscqlmpa.wingtracker.ui.undo.UndoCoordinator
import ch.qscqlmpa.wingtracker.ui.updates.InstallDialog
import ch.qscqlmpa.wingtracker.ui.updates.UpdateBanner
import ch.qscqlmpa.wingtracker.ui.updates.UpdateInstallPresenter
import ch.qscqlmpa.wingtracker.ui.updates.UpdatePresenter
import ch.qscqlmpa.wingtracker.ui.updates.UpdateUseCases
import ch.qscqlmpa.wingtracker.ui.updates.WhatsNewDialog
import ch.qscqlmpa.wingtracker.updates.AppVersion
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class Destination(
    val label: StringResource,
    val icon: ImageVector,
) {
    Logbook(Res.string.nav_logbook, Icons.Default.DateRange),
    Statistics(Res.string.nav_statistics, BarChartIcon),
    Gear(Res.string.nav_gear, Icons.Default.Build),
    Settings(Res.string.nav_settings, Icons.Default.Settings),
}

/** Left navigation rail: scales to more sections than tabs and becomes a bottom bar on mobile later. */
@Composable
fun AppShell(
    logbookUseCases: LogbookUseCases,
    getStatistics: GetStatistics,
    gearUseCases: GearUseCases,
    maintenanceUseCases: MaintenanceUseCases,
    reminders: RemindersUseCases,
    backups: BackupUseCases,
    cloudStorageUseCases: CloudStorageUseCases,
    cloudRestoreUseCases: CloudRestoreUseCases,
    updates: UpdateUseCases,
    platform: PlatformServices,
    themePreference: ThemePreference,
    scalePreference: InterfaceScalePreference,
    undo: UndoCoordinator,
    appVersion: String,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Logbook.name) }
    val current = Destination.valueOf(destination)
    val scope = rememberCoroutineScope()
    val attentionPresenter = remember { AttentionPresenter(scope, reminders.getAttention) }
    val reminderSettings =
        remember {
            ReminderSettingsPresenter(
                scope,
                reminders,
                platform.fileSaver,
                onLeadTimesSaved = attentionPresenter::refresh,
            )
        }
    val chooseTitle = stringResource(Res.string.settings_data_choose_title)
    val dataLocation =
        remember { DataLocationPresenter(scope, platform.dataLocation, chooseTitle, beforeChange = undo::flush) }
    val logLocation = remember { LogLocationPresenter(scope, platform.logLocation) }
    // One for the whole run, so a secret key kept for the session serves every upload.
    val cloudSecret = remember { CloudSecret(platform.credentialStore) }
    val backupSaveTitle = stringResource(Res.string.backup_save_title)
    val backup =
        remember {
            BackupPresenter(
                scope,
                backups,
                platform.fileSaver,
                backupSaveTitle,
                cloudSecret,
                beforeBackup = undo::flush,
                onCreated = attentionPresenter::refresh,
            )
        }
    val cloudStorage = remember { CloudStoragePresenter(scope, cloudStorageUseCases, cloudSecret, backup::load) }
    val restoreTitles =
        RestoreTitles(stringResource(Res.string.restore_pick_title), stringResource(Res.string.restore_folder_title))
    val restore =
        remember {
            RestorePresenter(
                scope,
                backups,
                platform.filePicker,
                platform.dataLocation,
                restoreTitles,
                cloudRestoreUseCases,
                cloudSecret,
                undo::flush,
            )
        }
    val updatePresenter = remember { UpdatePresenter(scope, updates) }
    val installPresenter =
        remember {
            UpdateInstallPresenter(scope, updates.downloadUpdate, platform.updateInstaller, beforeRestart = undo::flush)
        }
    // Shown once per start: the pilot can only fix it outside the application, then restart it.
    var fileNamesWarning by rememberSaveable { mutableStateOf(!platform.supportsAllFileNames) }
    // Set when the attention panel opens a piece of gear, consumed by the gear section.
    var pendingGear by remember { mutableStateOf<GearRef?>(null) }
    // Kept here, outside the logbook, so a search survives a look at the gear; it is gone after a restart.
    var logbookSearch by remember { mutableStateOf(LogbookSearch()) }
    // Set only by the logbook's search; opening Statistics from the rail always shows every flight.
    var statisticsFilter by remember { mutableStateOf<FlightFilter?>(null) }
    // Kept here, so a look at another section returns to the same settings page.
    var settingsCategory by remember { mutableStateOf(SettingsCategory.Appearance) }

    // Reminders are computed on demand (ADR 0012), so they are refreshed whenever the pilot moves between sections.
    LaunchedEffect(destination) { attentionPresenter.refresh() }
    LaunchedEffect(Unit) { reminderSettings.load() }
    LaunchedEffect(Unit) { backup.load() }
    LaunchedEffect(Unit) { cloudStorage.load() }
    // Once per start (ADR 0012 rules out checking in the background).
    LaunchedEffect(Unit) { updatePresenter.start() }

    val attentionCount = attentionPresenter.state.attention.count

    val shortcuts = remember { PageShortcuts() }

    CompositionLocalProvider(LocalUndo provides undo, LocalPageShortcuts provides shortcuts) {
        // Above the selection, so the page hears its keys also once the pilot selected text.
        Surface(modifier = Modifier.fillMaxSize().onPreviewKeyEvent(shortcuts::handle)) {
            // Every text and value can be selected and copied, e.g. a site name or a date into another program.
            SelectionContainer {
                Box {
                    Row {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            Destination.entries.forEach { item ->
                                NavigationRailItem(
                                    selected = item == current,
                                    onClick = {
                                        if (item == Destination.Statistics) statisticsFilter = null
                                        destination = item.name
                                    },
                                    icon = {
                                        RailIcon(item, attentionCount, updatePresenter.showsBadge)
                                    },
                                    colors =
                                        NavigationRailItemDefaults.colors(
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    label = { Text(stringResource(item.label)) },
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            RailVersion(appVersion)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            if (fileNamesWarning) {
                                FileNamesBanner(
                                    onDismiss = { fileNamesWarning = false },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                            if (updatePresenter.showsBanner) {
                                UpdateBanner(
                                    updatePresenter,
                                    installPresenter,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                when (current) {
                                    Destination.Logbook -> {
                                        LogbookRoute(
                                            useCases = logbookUseCases,
                                            platform = platform,
                                            attentionPanel = {
                                                AttentionPanel(
                                                    attentionPresenter.state.attention,
                                                    onOpen = { ref ->
                                                        pendingGear = ref
                                                        destination = Destination.Gear.name
                                                    },
                                                    backup = backup,
                                                )
                                            },
                                            search = logbookSearch,
                                            onSearchChange = { logbookSearch = it },
                                            onOpenStatistics = { filter ->
                                                statisticsFilter = filter
                                                destination = Destination.Statistics.name
                                            },
                                            onGoToGear = { destination = Destination.Gear.name },
                                        )
                                    }

                                    Destination.Statistics -> {
                                        StatisticsRoute(getStatistics, statisticsFilter, onShowAll = {
                                            statisticsFilter = null
                                        }, onGoToLogbook = {
                                            destination =
                                                Destination.Logbook.name
                                        })
                                    }

                                    Destination.Gear -> {
                                        GearRoute(
                                            useCases = gearUseCases,
                                            maintenanceUseCases = maintenanceUseCases,
                                            platform = platform,
                                            initialRef = pendingGear,
                                            onInitialConsumed = { pendingGear = null },
                                        )
                                    }

                                    Destination.Settings -> {
                                        SettingsScreen(
                                            settingsCategory,
                                            { settingsCategory = it },
                                            themePreference,
                                            scalePreference,
                                            reminderSettings,
                                            backup,
                                            restore,
                                            cloudStorage,
                                            dataLocation,
                                            logLocation,
                                            updatePresenter,
                                            installPresenter,
                                            appVersion,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    WhatsNewDialog(updatePresenter, installPresenter)
                    InstallDialog(installPresenter, updatePresenter.update)
                    SnackbarHost(undo.snackbarHost, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
                }
            }
        }
    }
}

// Release pages are named after the tag, which is the version with a leading v (ADR 0009).
private const val RELEASE_PAGE_URL = "https://github.com/michaelheiniger/wing-tracker/releases/tag/v"

/**
 * Always in sight, so a pilot reporting a problem or checking an update knows which version runs. It opens that
 * version's release page; a development build has none, so its version is plain text.
 */
@Composable
private fun RailVersion(appVersion: String) {
    val description = stringResource(Res.string.about_version, appVersion)
    val uriHandler = LocalUriHandler.current
    val modifier = Modifier.semantics { contentDescription = description }
    val label: @Composable () -> Unit = { Text(appVersion, style = MaterialTheme.typography.labelSmall) }
    if (AppVersion.parseOrNull(appVersion) == null) {
        Box(modifier.padding(vertical = 12.dp)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant, label)
        }
    } else {
        TextButton(onClick = { uriHandler.openUri(RELEASE_PAGE_URL + appVersion) }, modifier = modifier) { label() }
    }
}

/**
 * The count badge sits on the Gear item, where the things that need attention actually live; an available update is
 * marked on Settings, where it is explained and can be installed.
 */
@Composable
private fun RailIcon(
    destination: Destination,
    attentionCount: Int,
    updateAvailable: Boolean,
) {
    if (destination == Destination.Settings && updateAvailable) {
        val description = stringResource(Res.string.update_rail_badge)
        BadgedBox(
            badge = {
                // Not the default error colour: an update is good news, not a problem.
                Badge(
                    modifier = Modifier.semantics { contentDescription = description },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(UpdateIcon, contentDescription = null)
                }
            },
        ) {
            Icon(destination.icon, contentDescription = null)
        }
    } else if (destination == Destination.Gear && attentionCount > 0) {
        val description = stringResource(Res.string.attention_badge, attentionCount)
        BadgedBox(
            badge = {
                Badge {
                    Text(
                        attentionCount.toString(),
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    description
                            },
                    )
                }
            },
        ) {
            Icon(destination.icon, contentDescription = null)
        }
    } else {
        Icon(destination.icon, contentDescription = null)
    }
}
