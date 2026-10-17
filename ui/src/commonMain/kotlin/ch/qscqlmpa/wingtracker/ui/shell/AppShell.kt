package ch.qscqlmpa.wingtracker.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.ui.components.FileNamesBanner
import ch.qscqlmpa.wingtracker.ui.flights.LogbookRoute
import ch.qscqlmpa.wingtracker.ui.flights.LogbookUseCases
import ch.qscqlmpa.wingtracker.ui.gear.GearRoute
import ch.qscqlmpa.wingtracker.ui.gear.GearUseCases
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.MaintenanceUseCases
import ch.qscqlmpa.wingtracker.ui.icons.BarChartIcon
import ch.qscqlmpa.wingtracker.ui.icons.UpdateIcon
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.reminders.AttentionPresenter
import ch.qscqlmpa.wingtracker.ui.reminders.ReminderSettingsPresenter
import ch.qscqlmpa.wingtracker.ui.reminders.RemindersUseCases
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.attention_badge
import ch.qscqlmpa.wingtracker.ui.resources.nav_gear
import ch.qscqlmpa.wingtracker.ui.resources.nav_logbook
import ch.qscqlmpa.wingtracker.ui.resources.nav_settings
import ch.qscqlmpa.wingtracker.ui.resources.nav_statistics
import ch.qscqlmpa.wingtracker.ui.resources.settings_data_choose_title
import ch.qscqlmpa.wingtracker.ui.resources.update_rail_badge
import ch.qscqlmpa.wingtracker.ui.settings.DataLocationPresenter
import ch.qscqlmpa.wingtracker.ui.settings.LogLocationPresenter
import ch.qscqlmpa.wingtracker.ui.settings.SettingsScreen
import ch.qscqlmpa.wingtracker.ui.statistics.StatisticsRoute
import ch.qscqlmpa.wingtracker.ui.theme.InterfaceScalePreference
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
import ch.qscqlmpa.wingtracker.ui.undo.LocalUndo
import ch.qscqlmpa.wingtracker.ui.undo.UndoCoordinator
import ch.qscqlmpa.wingtracker.ui.updates.UpdateBanner
import ch.qscqlmpa.wingtracker.ui.updates.UpdatePresenter
import ch.qscqlmpa.wingtracker.ui.updates.UpdateUseCases
import ch.qscqlmpa.wingtracker.ui.updates.WhatsNewDialog
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
    val updatePresenter = remember { UpdatePresenter(scope, updates) }
    // Shown once per start: the pilot can only fix it outside the application, then restart it.
    var fileNamesWarning by rememberSaveable { mutableStateOf(!platform.supportsAllFileNames) }
    // Set when the attention panel opens a piece of gear, consumed by the gear section.
    var pendingGear by remember { mutableStateOf<GearRef?>(null) }

    // Reminders are computed on demand (ADR 0012), so they are refreshed whenever the pilot moves between sections.
    LaunchedEffect(destination) { attentionPresenter.refresh() }
    LaunchedEffect(Unit) { reminderSettings.load() }
    // Once per start (ADR 0012 rules out checking in the background).
    LaunchedEffect(Unit) { updatePresenter.start() }

    CompositionLocalProvider(LocalUndo provides undo) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box {
                Row {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Destination.entries.forEach { item ->
                            NavigationRailItem(
                                selected = item == current,
                                onClick = { destination = item.name },
                                icon = {
                                    RailIcon(item, attentionPresenter.state.attention.count, updatePresenter.showsBadge)
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
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (fileNamesWarning) {
                            FileNamesBanner(
                                onDismiss = { fileNamesWarning = false },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                        if (updatePresenter.showsBanner) {
                            UpdateBanner(updatePresenter, modifier = Modifier.fillMaxWidth().padding(16.dp))
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            when (current) {
                                Destination.Logbook -> {
                                    LogbookRoute(
                                        useCases = logbookUseCases,
                                        platform = platform,
                                        attention = attentionPresenter.state.attention,
                                        onOpenGear = { ref ->
                                            pendingGear = ref
                                            destination = Destination.Gear.name
                                        },
                                        onGoToGear = { destination = Destination.Gear.name },
                                    )
                                }

                                Destination.Statistics -> {
                                    StatisticsRoute(getStatistics, onGoToLogbook = {
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
                                        themePreference,
                                        scalePreference,
                                        reminderSettings,
                                        dataLocation,
                                        logLocation,
                                        updatePresenter,
                                        appVersion,
                                    )
                                }
                            }
                        }
                    }
                }
                WhatsNewDialog(updatePresenter)
                SnackbarHost(undo.snackbarHost, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
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
