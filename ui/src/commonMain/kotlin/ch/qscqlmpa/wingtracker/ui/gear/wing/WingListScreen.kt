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
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.MaintenanceStatus
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.gear.wing.maintenance.chipText
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.action_dismiss
import ch.qscqlmpa.wingtracker.ui.resources.delete_body
import ch.qscqlmpa.wingtracker.ui.resources.delete_confirm
import ch.qscqlmpa.wingtracker.ui.resources.delete_title
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_planned_on
import ch.qscqlmpa.wingtracker.ui.resources.retire_body
import ch.qscqlmpa.wingtracker.ui.resources.retire_reason_label
import ch.qscqlmpa.wingtracker.ui.resources.retire_title
import ch.qscqlmpa.wingtracker.ui.resources.unreadable_kept
import ch.qscqlmpa.wingtracker.ui.resources.unreadable_quarantined
import ch.qscqlmpa.wingtracker.ui.resources.wing_certification
import ch.qscqlmpa.wingtracker.ui.resources.wing_delete
import ch.qscqlmpa.wingtracker.ui.resources.wing_edit
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
import ch.qscqlmpa.wingtracker.ui.toDisplay
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
                                status = state.statuses[wing.id],
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
            wing = wing,
            onConfirm = { reason ->
                presenter.retire(wing.id, reason)
                wingToRetire = null
            },
            onDismiss = { wingToRetire = null },
        )
    }
    wingToDelete?.let { wing ->
        DeleteDialog(
            wing = wing,
            onConfirm = {
                presenter.delete(wing.id)
                wingToDelete = null
            },
            onDismiss = { wingToDelete = null },
        )
    }
}

@Composable
private fun UnreadableBanner(
    record: UnreadableRecord,
    onDismiss: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val message =
                if (record.quarantined) {
                    stringResource(Res.string.unreadable_quarantined, record.name)
                } else {
                    stringResource(Res.string.unreadable_kept, record.name)
                }
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_dismiss)) }
        }
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
    val containerColor =
        if (wing.isActive) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = containerColor)) {
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
                val since = stringResource(Res.string.wing_in_service_since, details.inServiceDate.toDisplay())
                val before =
                    stringResource(Res.string.wing_hours_before_tracking, formatDuration(details.hoursBeforeTracking))
                Text(
                    "$since · $before",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MaintenanceLine(wing, status)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(statusLabel(wing.status)) })
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

@Composable
private fun RetireDialog(
    wing: Wing,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.retire_title, wing.details.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.retire_body))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(Res.string.retire_reason_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(reason) }) { Text(stringResource(Res.string.wing_retire)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun DeleteDialog(
    wing: Wing,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_title, wing.details.name)) },
        text = { Text(stringResource(Res.string.delete_body)) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(Res.string.delete_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
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
            val color =
                if (status.isDue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            Text(status.chipText(), style = MaterialTheme.typography.labelLarge, color = color)
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
