package ch.qscqlmpa.wingtracker.ui.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.ui.flights.LogbookRoute
import ch.qscqlmpa.wingtracker.ui.flights.LogbookUseCases
import ch.qscqlmpa.wingtracker.ui.gear.GearRoute
import ch.qscqlmpa.wingtracker.ui.gear.GearUseCases
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.MaintenanceUseCases
import ch.qscqlmpa.wingtracker.ui.icons.BarChartIcon
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
import ch.qscqlmpa.wingtracker.ui.settings.SettingsScreen
import ch.qscqlmpa.wingtracker.ui.statistics.StatisticsRoute
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
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
    platform: PlatformServices,
    themePreference: ThemePreference,
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
    // Set when the attention panel opens a piece of gear, consumed by the gear section.
    var pendingGear by remember { mutableStateOf<GearRef?>(null) }

    // Reminders are computed on demand (ADR 0012), so they are refreshed whenever the pilot moves between sections.
    LaunchedEffect(destination) { attentionPresenter.refresh() }
    LaunchedEffect(Unit) { reminderSettings.load() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Row {
            NavigationRail {
                Destination.entries.forEach { item ->
                    NavigationRailItem(
                        selected = item == current,
                        onClick = { destination = item.name },
                        icon = { RailIcon(item, attentionPresenter.state.attention.count) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
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
                    StatisticsRoute(getStatistics, onGoToLogbook = { destination = Destination.Logbook.name })
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
                    SettingsScreen(themePreference, reminderSettings)
                }
            }
        }
    }
}

/** The count badge sits on the Gear item, where the things that need attention actually live. */
@Composable
private fun RailIcon(
    destination: Destination,
    attentionCount: Int,
) {
    if (destination == Destination.Gear && attentionCount > 0) {
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
