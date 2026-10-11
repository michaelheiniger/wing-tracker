package ch.qscqlmpa.wingtracker.ui.gear.simple

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import ch.qscqlmpa.wingtracker.ui.components.AppCard
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.components.UnreadableBanner
import ch.qscqlmpa.wingtracker.ui.components.severity
import ch.qscqlmpa.wingtracker.ui.gear.DeleteDialog
import ch.qscqlmpa.wingtracker.ui.gear.RetireDialog
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.chipText
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_planned_on
import ch.qscqlmpa.wingtracker.ui.resources.wing_delete
import ch.qscqlmpa.wingtracker.ui.resources.wing_edit
import ch.qscqlmpa.wingtracker.ui.resources.wing_in_service_since
import ch.qscqlmpa.wingtracker.ui.resources.wing_open_maintenance
import ch.qscqlmpa.wingtracker.ui.resources.wing_retire
import ch.qscqlmpa.wingtracker.ui.resources.wing_status_active
import ch.qscqlmpa.wingtracker.ui.resources.wing_status_retired
import ch.qscqlmpa.wingtracker.ui.resources.wing_status_retired_reason
import ch.qscqlmpa.wingtracker.ui.resources.wings_show_retired
import ch.qscqlmpa.wingtracker.ui.theme.extraColors
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.stringResource

@Composable
fun SimpleGearListScreen(
    presenter: SimpleGearListPresenter,
    texts: SimpleGearTexts,
    onAdd: () -> Unit,
    onEdit: (SimpleGearItem) -> Unit,
    onOpenMaintenance: (SimpleGearItem) -> Unit,
) {
    val state = presenter.state
    var toRetire by remember { mutableStateOf<SimpleGearItem?>(null) }
    var toDelete by remember { mutableStateOf<SimpleGearItem?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.extraColors.accent,
                contentColor = MaterialTheme.extraColors.onAccent,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(texts.add)) },
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
                Text(stringResource(texts.title), style = MaterialTheme.typography.headlineMedium)
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

                state.visibleItems.isEmpty() -> {
                    Empty(texts, onAdd)
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        items(state.visibleItems, key = { it.ref.id }) { item ->
                            ItemCard(
                                item = item,
                                status = state.statuses[item.ref],
                                onEdit = { onEdit(item) },
                                onOpenMaintenance = { onOpenMaintenance(item) },
                                onRetire = { toRetire = item },
                                onDelete = { toDelete = item },
                            )
                        }
                    }
                }
            }
        }
    }

    toRetire?.let { item ->
        RetireDialog(
            name = item.details.name,
            onConfirm = { reason ->
                presenter.retire(item.ref, reason)
                toRetire = null
            },
            onDismiss = { toRetire = null },
        )
    }
    toDelete?.let { item ->
        DeleteDialog(
            name = item.details.name,
            onConfirm = {
                presenter.delete(item.ref)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun Empty(
    texts: SimpleGearTexts,
    onAdd: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(texts.emptyTitle), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(texts.emptyBody), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAdd) { Text(stringResource(texts.emptyAction)) }
    }
}

@Composable
private fun ItemCard(
    item: SimpleGearItem,
    status: MaintenanceStatus?,
    onEdit: () -> Unit,
    onOpenMaintenance: () -> Unit,
    onRetire: () -> Unit,
    onDelete: () -> Unit,
) {
    val details = item.details
    // Retired gear sits back on a quieter surface; active gear uses the standard card.
    val containerColor =
        if (item.isActive) {
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
                    ).joinToString(" · ")
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(Res.string.wing_in_service_since, details.inServiceDate.toDisplay()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MaintenanceLine(item, status)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(statusLabel(item.status), if (item.isActive) Severity.Success else Severity.Neutral)
                TextButton(onClick = onOpenMaintenance) { Text(stringResource(Res.string.wing_open_maintenance)) }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.wing_edit, details.name))
                }
                if (item.isActive) TextButton(onClick = onRetire) { Text(stringResource(Res.string.wing_retire)) }
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
private fun MaintenanceLine(
    item: SimpleGearItem,
    status: MaintenanceStatus?,
) {
    val planned = item.nextPlannedCheck
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
