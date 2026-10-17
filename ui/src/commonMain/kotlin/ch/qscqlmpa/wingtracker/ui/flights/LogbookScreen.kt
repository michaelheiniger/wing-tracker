package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.Logbook
import ch.qscqlmpa.wingtracker.flights.LogbookMonth
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.reminders.Attention
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.components.AppCard
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.components.UnreadableBanner
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.icons.BarChartIcon
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.reminders.AttentionPanel
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.action_dismiss
import ch.qscqlmpa.wingtracker.ui.resources.delete_confirm
import ch.qscqlmpa.wingtracker.ui.resources.flight_altitude
import ch.qscqlmpa.wingtracker.ui.resources.flight_date_time
import ch.qscqlmpa.wingtracker.ui.resources.flight_delete
import ch.qscqlmpa.wingtracker.ui.resources.flight_delete_body
import ch.qscqlmpa.wingtracker.ui.resources.flight_delete_title
import ch.qscqlmpa.wingtracker.ui.resources.flight_distance
import ch.qscqlmpa.wingtracker.ui.resources.flight_edit
import ch.qscqlmpa.wingtracker.ui.resources.flight_sites
import ch.qscqlmpa.wingtracker.ui.resources.flight_unknown_wing
import ch.qscqlmpa.wingtracker.ui.resources.flights_count
import ch.qscqlmpa.wingtracker.ui.resources.igc_open_track
import ch.qscqlmpa.wingtracker.ui.resources.igc_track_chip
import ch.qscqlmpa.wingtracker.ui.resources.logbook_empty_action
import ch.qscqlmpa.wingtracker.ui.resources.logbook_empty_body
import ch.qscqlmpa.wingtracker.ui.resources.logbook_empty_title
import ch.qscqlmpa.wingtracker.ui.resources.logbook_log_flight
import ch.qscqlmpa.wingtracker.ui.resources.logbook_month_header
import ch.qscqlmpa.wingtracker.ui.resources.logbook_month_summary
import ch.qscqlmpa.wingtracker.ui.resources.logbook_no_wings_action
import ch.qscqlmpa.wingtracker.ui.resources.logbook_no_wings_body
import ch.qscqlmpa.wingtracker.ui.resources.logbook_no_wings_title
import ch.qscqlmpa.wingtracker.ui.resources.logbook_search_clear
import ch.qscqlmpa.wingtracker.ui.resources.logbook_search_none_body
import ch.qscqlmpa.wingtracker.ui.resources.logbook_search_none_title
import ch.qscqlmpa.wingtracker.ui.resources.logbook_search_result
import ch.qscqlmpa.wingtracker.ui.resources.logbook_search_statistics
import ch.qscqlmpa.wingtracker.ui.resources.logbook_title
import ch.qscqlmpa.wingtracker.ui.resources.month_names
import ch.qscqlmpa.wingtracker.ui.resources.undo_flight_deleted
import ch.qscqlmpa.wingtracker.ui.resources.unreadable_kept
import ch.qscqlmpa.wingtracker.ui.resources.unreadable_quarantined
import ch.qscqlmpa.wingtracker.ui.resources.wing_hours_total
import ch.qscqlmpa.wingtracker.ui.theme.extraColors
import ch.qscqlmpa.wingtracker.ui.toDisplay
import ch.qscqlmpa.wingtracker.ui.undo.LocalUndo
import ch.qscqlmpa.wingtracker.ui.undo.undoText
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

@Composable
fun LogbookScreen(
    presenter: LogbookPresenter,
    importFailure: Failure?,
    onLogFlight: () -> Unit,
    onEditFlight: (Flight) -> Unit,
    onImportIgc: () -> Unit,
    onImportDevice: () -> Unit,
    onImportSyride: () -> Unit,
    onImportSpreadsheet: () -> Unit,
    onOpenStatistics: () -> Unit,
    attention: Attention,
    onOpenAttention: (GearRef) -> Unit,
    onGoToGear: () -> Unit,
) {
    val state = presenter.state
    val logbook = state.visible
    var flightToDelete by remember { mutableStateOf<Flight?>(null) }

    Scaffold(
        floatingActionButton = {
            if (logbook != null && logbook.wings.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onLogFlight,
                    containerColor = MaterialTheme.extraColors.accent,
                    contentColor = MaterialTheme.extraColors.onAccent,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(Res.string.logbook_log_flight)) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.logbook_title), style = MaterialTheme.typography.headlineMedium)
                if (logbook != null) {
                    ImportMenu(
                        logbook.wings.isNotEmpty(),
                        onImportIgc,
                        onImportDevice,
                        onImportSyride,
                        onImportSpreadsheet,
                    )
                }
            }
            importFailure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            AttentionPanel(attention, onOpenAttention)
            state.unreadable.forEach { UnreadableBanner(it, onDismiss = presenter::dismissUnreadable) }
            state.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            } else if (logbook != null) {
                LogbookBody(
                    logbook,
                    LogbookSearchBinding(state.search, checkNotNull(state.shown), presenter::search, onOpenStatistics),
                    onLogFlight,
                    onEditFlight,
                    presenter::openTrack,
                    onGoToGear,
                    onDelete = { flightToDelete = it },
                )
            }
        }
    }

    flightToDelete?.let { flight ->
        val undo = LocalUndo.current
        val undoText = undoText(Res.string.undo_flight_deleted)
        val wingName =
            logbook
                ?.wings
                ?.firstOrNull { it.id == flight.details.wingId }
                ?.details
                ?.name
                ?: stringResource(Res.string.flight_unknown_wing)
        AlertDialog(
            onDismissRequest = { flightToDelete = null },
            title = { Text(stringResource(Res.string.flight_delete_title)) },
            text = { Text(stringResource(Res.string.flight_delete_body, flight.details.date.toDisplay(), wingName)) },
            confirmButton = {
                Button(
                    onClick = {
                        presenter.delete(flight.id, undo, undoText)
                        flightToDelete = null
                    },
                ) { Text(stringResource(Res.string.delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { flightToDelete = null }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

/** The search as typed, the flights it leaves and how to change it. */
private class LogbookSearchBinding(
    val search: LogbookSearch,
    val shown: Logbook,
    val onChange: (LogbookSearch) -> Unit,
    val onOpenStatistics: () -> Unit,
)

@Composable
private fun LogbookBody(
    logbook: Logbook,
    search: LogbookSearchBinding,
    onLogFlight: () -> Unit,
    onEdit: (Flight) -> Unit,
    onOpenTrack: (Flight) -> Unit,
    onGoToGear: () -> Unit,
    onDelete: (Flight) -> Unit,
) {
    when {
        logbook.wings.isEmpty() -> {
            EmptyState(
                title = stringResource(Res.string.logbook_no_wings_title),
                body = stringResource(Res.string.logbook_no_wings_body),
                action = stringResource(Res.string.logbook_no_wings_action),
                onAction = onGoToGear,
            )
        }

        logbook.isEmpty -> {
            EmptyState(
                title = stringResource(Res.string.logbook_empty_title),
                body = stringResource(Res.string.logbook_empty_body),
                action = stringResource(Res.string.logbook_empty_action),
                onAction = onLogFlight,
            )
        }

        else -> {
            LogbookContent(logbook, search, onEdit, onOpenTrack, onDelete)
        }
    }
}

@Composable
private fun LogbookContent(
    logbook: Logbook,
    search: LogbookSearchBinding,
    onEdit: (Flight) -> Unit,
    onOpenTrack: (Flight) -> Unit,
    onDelete: (Flight) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        logbook.wings.filter { it.isActive }.forEach { wing ->
            val total = logbook.hoursByWing[wing.id] ?: Duration.ZERO
            StatusBadge(
                stringResource(Res.string.wing_hours_total, wing.details.name, formatDuration(total)),
                Severity.Neutral,
            )
        }
    }
    LogbookSearchBar(search.search, logbook.wings, search.onChange)
    val shown = search.shown
    if (search.search.isActive) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    Res.string.logbook_search_result,
                    pluralStringResource(Res.plurals.flights_count, shown.flightCount, shown.flightCount),
                    formatDuration(shown.total),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!shown.isEmpty) {
                TextButton(onClick = search.onOpenStatistics) {
                    Icon(BarChartIcon, contentDescription = null)
                    Text(stringResource(Res.string.logbook_search_statistics))
                }
            }
        }
    }
    if (shown.isEmpty) {
        EmptyState(
            title = stringResource(Res.string.logbook_search_none_title),
            body = stringResource(Res.string.logbook_search_none_body),
            action = stringResource(Res.string.logbook_search_clear),
            onAction = { search.onChange(LogbookSearch()) },
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        shown.months.forEach { month ->
            item(key = "month-${month.month}") { MonthHeader(month) }
            items(month.flights, key = { it.id.value }) { flight ->
                val wingName =
                    logbook.wings
                        .firstOrNull { it.id == flight.details.wingId }
                        ?.details
                        ?.name
                        ?: stringResource(Res.string.flight_unknown_wing)
                FlightCard(flight, wingName, onEdit = {
                    onEdit(flight)
                }, onOpenTrack = { onOpenTrack(flight) }, onDelete = { onDelete(flight) })
            }
        }
    }
}

@Composable
private fun MonthHeader(month: LogbookMonth) {
    val names = stringArrayResource(Res.array.month_names)
    val name = names[month.month.month.ordinal]
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.logbook_month_header, name, month.month.year),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(
                Res.string.logbook_month_summary,
                pluralStringResource(Res.plurals.flights_count, month.flights.size, month.flights.size),
                formatDuration(month.total),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FlightCard(
    flight: Flight,
    wingName: String,
    onEdit: () -> Unit,
    onOpenTrack: () -> Unit,
    onDelete: () -> Unit,
) {
    val details = flight.details
    val openTrackDescription = stringResource(Res.string.igc_open_track)
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val time = details.takeOffTime
            Text(
                if (time == null) {
                    details.date.toDisplay()
                } else {
                    stringResource(Res.string.flight_date_time, details.date.toDisplay(), time.toDisplay())
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(wingName, style = MaterialTheme.typography.titleMedium)
                val sites =
                    when {
                        details.takeOffSite != null && details.landingSite != null -> {
                            stringResource(Res.string.flight_sites, details.takeOffSite!!, details.landingSite!!)
                        }

                        else -> {
                            details.takeOffSite ?: details.landingSite
                        }
                    }
                sites?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                importedFacts(details)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                details.notes?.let {
                    Text(
                        it,
                        maxLines = 2,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (flight.igcFile != null) {
                AssistChip(
                    onClick = onOpenTrack,
                    label = { Text(stringResource(Res.string.igc_track_chip)) },
                    modifier = Modifier.semantics { contentDescription = openTrackDescription },
                )
            }
            Text(formatDuration(details.duration), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.flight_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.flight_delete))
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAction) { Text(action) }
    }
}

/** Distance, top altitude and instrument, when the flight came with them (e.g. from Syride). */
@Composable
private fun importedFacts(details: FlightDetails): String? {
    val parts =
        listOfNotNull(
            details.distanceKm?.let { stringResource(Res.string.flight_distance, it.toDisplayNumber()) },
            details.topAltitudeMeters?.let { stringResource(Res.string.flight_altitude, it) },
            details.instrument,
        )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

internal fun Double.toDisplayNumber(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
