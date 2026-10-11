package ch.qscqlmpa.wingtracker.ui.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.ui.flights.LogbookRoute
import ch.qscqlmpa.wingtracker.ui.flights.LogbookUseCases
import ch.qscqlmpa.wingtracker.ui.gear.GearRoute
import ch.qscqlmpa.wingtracker.ui.gear.GearUseCases
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.MaintenanceUseCases
import ch.qscqlmpa.wingtracker.ui.icons.BarChartIcon
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.resources.Res
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
    platform: PlatformServices,
    themePreference: ThemePreference,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Logbook.name) }
    val current = Destination.valueOf(destination)

    Surface(modifier = Modifier.fillMaxSize()) {
        Row {
            NavigationRail {
                Destination.entries.forEach { item ->
                    NavigationRailItem(
                        selected = item == current,
                        onClick = { destination = item.name },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
            when (current) {
                Destination.Logbook -> {
                    LogbookRoute(logbookUseCases, onGoToGear = { destination = Destination.Gear.name })
                }

                Destination.Statistics -> {
                    StatisticsRoute(getStatistics, onGoToLogbook = { destination = Destination.Logbook.name })
                }

                Destination.Gear -> {
                    GearRoute(gearUseCases, maintenanceUseCases, platform)
                }

                Destination.Settings -> {
                    SettingsScreen(themePreference)
                }
            }
        }
    }
}
