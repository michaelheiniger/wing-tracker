package ch.qscqlmpa.wingtracker.ui.gear.maintenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.GearMaintenance
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.ui.components.AppAlertDialog
import ch.qscqlmpa.wingtracker.ui.components.AppCard
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.components.severity
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.check_delete_body
import ch.qscqlmpa.wingtracker.ui.resources.check_delete_title
import ch.qscqlmpa.wingtracker.ui.resources.delete_confirm
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_back
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_cancel_plan
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_complete
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_delete_check
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_edit_check
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_history_empty
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_history_title
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_interval_both
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_interval_hours
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_interval_months
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_no_interval
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_no_start
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_open_report
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_plan_check
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_planned_empty
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_planned_title
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_record_check
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_restarts
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_since
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_total_hours
import ch.qscqlmpa.wingtracker.ui.resources.undo_check_deleted
import ch.qscqlmpa.wingtracker.ui.resources.undo_plan_cancelled
import ch.qscqlmpa.wingtracker.ui.toDisplay
import ch.qscqlmpa.wingtracker.ui.undo.LocalUndo
import ch.qscqlmpa.wingtracker.ui.undo.undoText
import org.jetbrains.compose.resources.stringResource

private sealed interface MaintenanceDialog {
    data object RecordCheck : MaintenanceDialog

    data class EditCheck(
        val check: MaintenanceCheck,
    ) : MaintenanceDialog

    data class CompletePlan(
        val planned: PlannedCheck,
    ) : MaintenanceDialog

    data object NewPlan : MaintenanceDialog

    data class EditPlan(
        val planned: PlannedCheck,
    ) : MaintenanceDialog

    data class DeleteCheck(
        val check: MaintenanceCheck,
    ) : MaintenanceDialog
}

/** Everything the dialogs need from the route: how to build their presenters, and the screen to return to. */
class MaintenanceDialogs(
    val checkPresenter: (CheckFormMode) -> CheckFormPresenter,
    val planPresenter: (PlannedCheck?) -> PlanFormPresenter,
)

@Composable
fun GearMaintenanceScreen(
    presenter: GearMaintenancePresenter,
    dialogs: MaintenanceDialogs,
    onBack: () -> Unit,
) {
    val state = presenter.state
    val data = state.shown
    var dialog by remember { mutableStateOf<MaintenanceDialog?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text(stringResource(Res.string.maintenance_back), modifier = Modifier.padding(start = 8.dp))
        }
        state.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        } else if (data != null) {
            MaintenanceContent(data, presenter, onDialog = { dialog = it })
        }
    }

    val current = dialog
    val close = {
        dialog = null
        presenter.refresh()
    }
    val companies = data?.knownCompanies.orEmpty()
    if (current != null) {
        when (current) {
            MaintenanceDialog.RecordCheck -> {
                CheckDialogHost(dialogs, CheckFormMode.Record, companies, close)
            }

            is MaintenanceDialog.EditCheck -> {
                CheckDialogHost(dialogs, CheckFormMode.Edit(current.check), companies, close)
            }

            is MaintenanceDialog.CompletePlan -> {
                CheckDialogHost(dialogs, CheckFormMode.Complete(current.planned), companies, close)
            }

            MaintenanceDialog.NewPlan -> {
                PlanDialogHost(dialogs, null, companies, close)
            }

            is MaintenanceDialog.EditPlan -> {
                PlanDialogHost(dialogs, current.planned, companies, close)
            }

            is MaintenanceDialog.DeleteCheck -> {
                val undo = LocalUndo.current
                val undoText = undoText(Res.string.undo_check_deleted)
                DeleteCheckDialog(
                    check = current.check,
                    onConfirm = {
                        presenter.deleteCheck(current.check.id, undo, undoText)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }
        }
    }
}

// The presenter is remembered per dialog, so typing never recreates it and a new dialog starts fresh.
@Composable
private fun CheckDialogHost(
    dialogs: MaintenanceDialogs,
    mode: CheckFormMode,
    companies: List<String>,
    onClose: () -> Unit,
) {
    val presenter = remember(mode) { dialogs.checkPresenter(mode) }
    CheckDialog(presenter, companies, onClose)
}

@Composable
private fun PlanDialogHost(
    dialogs: MaintenanceDialogs,
    planned: PlannedCheck?,
    companies: List<String>,
    onClose: () -> Unit,
) {
    val presenter = remember(planned) { dialogs.planPresenter(planned) }
    PlanDialog(presenter, companies, onClose)
}

@Composable
private fun MaintenanceContent(
    data: GearMaintenance,
    presenter: GearMaintenancePresenter,
    onDialog: (MaintenanceDialog) -> Unit,
) {
    val history = data.gear.history
    val undo = LocalUndo.current
    val planCancelledText = undoText(Res.string.undo_plan_cancelled)
    Text(data.gear.displayName, style = MaterialTheme.typography.headlineMedium)
    SummaryCard(data)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { onDialog(MaintenanceDialog.RecordCheck) }) {
            Text(stringResource(Res.string.maintenance_record_check))
        }
        OutlinedButton(onClick = { onDialog(MaintenanceDialog.NewPlan) }) {
            Text(stringResource(Res.string.maintenance_plan_check))
        }
    }

    Text(stringResource(Res.string.maintenance_planned_title), style = MaterialTheme.typography.titleLarge)
    if (history.plannedChecks.isEmpty()) {
        Text(stringResource(Res.string.maintenance_planned_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    history.plannedChecks.sortedBy { it.date }.forEach { planned ->
        PlannedCard(
            planned,
            onComplete = { onDialog(MaintenanceDialog.CompletePlan(planned)) },
            onEdit = { onDialog(MaintenanceDialog.EditPlan(planned)) },
            onCancel = { presenter.cancelPlan(planned.id, undo, planCancelledText) },
        )
    }

    Text(stringResource(Res.string.maintenance_history_title), style = MaterialTheme.typography.titleLarge)
    if (history.checks.isEmpty()) {
        Text(stringResource(Res.string.maintenance_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    history.checks.forEach { check ->
        CheckCard(
            check,
            onOpenReport = presenter::openReport,
            onEdit = { onDialog(MaintenanceDialog.EditCheck(check)) },
            onDelete = { onDialog(MaintenanceDialog.DeleteCheck(check)) },
        )
    }
}

@Composable
private fun SummaryCard(data: GearMaintenance) {
    val interval = data.gear.maintenanceInterval
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Only wings fly in the logbook, so only they have hours on the clock.
            data.totalHours?.let { total ->
                Text(
                    stringResource(Res.string.maintenance_total_hours, formatDuration(total)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (interval == null) {
                Text(
                    stringResource(Res.string.maintenance_no_interval),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val months = interval.months
                val hours = interval.hours?.let { formatDuration(it) }
                Text(
                    when {
                        months != null && hours != null -> {
                            stringResource(
                                Res.string.maintenance_interval_both,
                                months,
                                hours,
                            )
                        }

                        months != null -> {
                            stringResource(Res.string.maintenance_interval_months, months)
                        }

                        else -> {
                            stringResource(Res.string.maintenance_interval_hours, hours.orEmpty())
                        }
                    },
                )
            }
            if (interval != null && data.status == null) {
                Text(
                    stringResource(Res.string.maintenance_no_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            data.status?.let { status ->
                StatusBadge(status.chipText(), status.state.severity())
                Text(
                    stringResource(
                        Res.string.maintenance_since,
                        status.since.toDisplay(),
                        formatDuration(status.hoursSince),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlannedCard(
    planned: PlannedCheck,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(planned.date.toDisplay(), style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(planned.type.label(), style = MaterialTheme.typography.titleSmall)
                listOfNotNull(planned.company, planned.notes).forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(onClick = onComplete) { Text(stringResource(Res.string.maintenance_complete)) }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.maintenance_edit_check))
            }
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.maintenance_cancel_plan)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckCard(
    check: MaintenanceCheck,
    onOpenReport: (ch.qscqlmpa.wingtracker.attachments.Attachment) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(check.date.toDisplay(), style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(check.type.label(), style = MaterialTheme.typography.titleSmall)
                listOfNotNull(check.company, check.notes).forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (check.type == CheckType.Other && check.restartsInterval) {
                    Text(stringResource(Res.string.maintenance_restarts), style = MaterialTheme.typography.labelSmall)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    check.reports.forEach { report ->
                        AssistChip(
                            onClick = { onOpenReport(report) },
                            label = { Text(stringResource(Res.string.maintenance_open_report, report.fileName)) },
                        )
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.maintenance_edit_check))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.maintenance_delete_check))
            }
        }
    }
}

@Composable
private fun DeleteCheckDialog(
    check: MaintenanceCheck,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.check_delete_title)) },
        text = { Text(stringResource(Res.string.check_delete_body, check.type.label(), check.date.toDisplay())) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(Res.string.delete_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
