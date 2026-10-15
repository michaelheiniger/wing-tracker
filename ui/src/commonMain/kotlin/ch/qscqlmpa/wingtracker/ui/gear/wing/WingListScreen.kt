package ch.qscqlmpa.wingtracker.ui.gear.wing

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.DueState
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceStatus
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import ch.qscqlmpa.wingtracker.ui.components.AppCard
import ch.qscqlmpa.wingtracker.ui.components.HelpTooltip
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.components.UnreadableBanner
import ch.qscqlmpa.wingtracker.ui.components.severity
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.gear.DeleteDialog
import ch.qscqlmpa.wingtracker.ui.gear.RetireDialog
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.chipText
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.action_dismiss
import ch.qscqlmpa.wingtracker.ui.resources.delete_body
import ch.qscqlmpa.wingtracker.ui.resources.delete_confirm
import ch.qscqlmpa.wingtracker.ui.resources.delete_title
import ch.qscqlmpa.wingtracker.ui.resources.help_in_service_date
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_planned_on
import ch.qscqlmpa.wingtracker.ui.resources.retire_body
import ch.qscqlmpa.wingtracker.ui.resources.retire_reason_label
import ch.qscqlmpa.wingtracker.ui.resources.retire_title
import ch.qscqlmpa.wingtracker.ui.resources.undo_item_deleted
import ch.qscqlmpa.wingtracker.ui.resources.unreadable_kept
import ch.qscqlmpa.wingtracker.ui.resources.unreadable_quarantined
import ch.qscqlmpa.wingtracker.ui.resources.wing_certification
import ch.qscqlmpa.wingtracker.ui.resources.wing_delete
import ch.qscqlmpa.wingtracker.ui.resources.wing_edit
import ch.qscqlmpa.wingtracker.ui.resources.wing_flights_before_tracking
import ch.qscqlmpa.wingtracker.ui.resources.wing_hours_before_tracking
import ch.qscqlmpa.wingtracker.ui.resources.wing_in_service_since
import ch.qscqlmpa.wingtracker.ui.resources.wing_open_maintenance
import ch.qscqlmpa.wingtracker.ui.resources.wing_retire
import ch.qscqlmpa.wingtracker.ui.resources.wing_status_active
import ch.qscqlmpa.wingtracker.ui.resources.wing_status_retired
import ch.qscqlmpa.wingtracker.ui.resources.wing_status_retired_reason
import ch.qscqlmpa.wingtracker.ui.resources.wings_add
import ch.qscqlmpa.wingtracker.ui.resources.wings_empty_action
import ch.qscqlmpa.wingtracker.ui.resources.wings_empty_body
import ch.qscqlmpa.wingtracker.ui.resources.wings_empty_title
import ch.qscqlmpa.wingtracker.ui.resources.wings_show_retired
import ch.qscqlmpa.wingtracker.ui.resources.wings_title
import ch.qscqlmpa.wingtracker.ui.theme.extraColors
import ch.qscqlmpa.wingtracker.ui.toDisplay
import ch.qscqlmpa.wingtracker.ui.undo.LocalUndo
import ch.qscqlmpa.wingtracker.ui.undo.undoText
import org.jetbrains.compose.resources.stringResource

@Composable
fun WingListScreen(
    presenter: WingListPresenter,
    onAdd: () -> Unit,
    onEdit: (Wing) -> Unit,
    onOpenMaintenance: (Wing) -> Unit,
) {
    val state = presenter.state
    var wingToRetire by remember { mutableStateOf<Wing?>(null) }
    var wingToDelete by remember { mutableStateOf<Wing?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.extraColors.accent,
                contentColor = MaterialTheme.extraColors.onAccent,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.wings_add)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.wings_title), style = MaterialTheme.typography.headlineMedium)
                if (state.retiredCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(Res.string.wings_show_retired, state.retiredCount))
                        Switch(checked = state.showRetired, onCheckedChange = { presenter.toggleShowRetired() })
                    }
                }
            }
            state.unreadable.forEach { UnreadableBanner(it, onDismiss = presenter::dismissUnreadable) }
            state.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            when {
                state.loading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                }

                state.visibleWings.isEmpty() -> {
                    EmptyWings(onAdd)
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        items(state.visibleWings, key = { it.id.value }) { wing ->
                            WingCard(
                                wing = wing,
                                status = state.statuses[wing.ref],
                                onEdit = { onEdit(wing) },
                                onOpenMaintenance = { onOpenMaintenance(wing) },
                                onRetire = { wingToRetire = wing },
                                onDelete = { wingToDelete = wing },
                            )
                        }
                    }
                }
            }
        }
    }

    wingToRetire?.let { wing ->
        RetireDialog(
            name = wing.details.name,
            onConfirm = { reason ->
                presenter.retire(wing.id, reason)
                wingToRetire = null
            },
            onDismiss = { wingToRetire = null },
        )
    }
    wingToDelete?.let { wing ->
        val undo = LocalUndo.current
        val undoText = undoText(Res.string.undo_item_deleted, wing.details.name)
        DeleteDialog(
            name = wing.details.name,
            onConfirm = {
                presenter.delete(wing.id, undo, undoText)
                wingToDelete = null
            },
            onDismiss = { wingToDelete = null },
        )
    }
}

@Composable
private fun EmptyWings(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.wings_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(Res.string.wings_empty_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAdd) { Text(stringResource(Res.string.wings_empty_action)) }
    }
}

@Composable
private fun WingCard(
    wing: Wing,
    status: MaintenanceStatus?,
    onEdit: () -> Unit,
    onOpenMaintenance: () -> Unit,
    onRetire: () -> Unit,
    onDelete: () -> Unit,
) {
    val details = wing.details
    // Retired gear sits back on a quieter surface; active gear uses the standard card.
    val containerColor =
        if (wing.isActive) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        }
    AppCard(modifier = Modifier.fillMaxWidth(), containerColor = containerColor) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(details.name, style = MaterialTheme.typography.titleLarge)
                val subtitle =
                    listOfNotNull(
                        "${details.manufacturer} ${details.model}",
                        details.size,
                        details.certificationClass?.let { stringResource(Res.string.wing_certification, it.name) },
                    ).joinToString(" · ")
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val since =
                    details.inServiceDate?.let { stringResource(Res.string.wing_in_service_since, it.toDisplay()) }
                val before =
                    stringResource(Res.string.wing_hours_before_tracking, formatDuration(details.hoursBeforeTracking))
                val flightsBefore =
                    details.flightsBeforeTracking.takeIf { it > 0 }?.let {
                        stringResource(Res.string.wing_flights_before_tracking, it)
                    }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        listOfNotNull(since, before, flightsBefore).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (since != null) HelpTooltip(stringResource(Res.string.help_in_service_date), iconSize = 16)
                }
                MaintenanceLine(wing, status)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(statusLabel(wing.status), if (wing.isActive) Severity.Success else Severity.Neutral)
                TextButton(onClick = onOpenMaintenance) { Text(stringResource(Res.string.wing_open_maintenance)) }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.wing_edit, details.name))
                }
                if (wing.isActive) TextButton(onClick = onRetire) { Text(stringResource(Res.string.wing_retire)) }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.wing_delete, details.name),
                    )
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: GearStatus): String =
    when (status) {
        GearStatus.Active -> {
            stringResource(Res.string.wing_status_active)
        }

        is GearStatus.Retired -> {
            if (status.reason == null) {
                stringResource(Res.string.wing_status_retired, status.on.toDisplay())
            } else {
                stringResource(Res.string.wing_status_retired_reason, status.on.toDisplay(), status.reason!!)
            }
        }
    }

/** Shows when a check is due, or the next planned one, so the list answers "does this wing need attention?". */
@Composable
private fun MaintenanceLine(
    wing: Wing,
    status: MaintenanceStatus?,
) {
    val planned = wing.nextPlannedCheck
    if (status == null && planned == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (status != null) {
            if (status.state == DueState.Ok) {
                Text(
                    status.chipText(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                StatusBadge(status.chipText(), status.state.severity())
            }
        }
        if (planned != null) {
            Text(
                stringResource(Res.string.maintenance_planned_on, planned.date.toDisplay()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
