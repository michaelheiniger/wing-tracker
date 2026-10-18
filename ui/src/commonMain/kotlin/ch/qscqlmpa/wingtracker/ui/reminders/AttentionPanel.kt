package ch.qscqlmpa.wingtracker.ui.reminders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.backup.BackupDue
import ch.qscqlmpa.wingtracker.gear.maintenance.DueState
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.reminders.Attention
import ch.qscqlmpa.wingtracker.reminders.AttentionItem
import ch.qscqlmpa.wingtracker.ui.backup.BackupPresenter
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.icon
import ch.qscqlmpa.wingtracker.ui.components.severity
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.chipText
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.attention_backup_last
import ch.qscqlmpa.wingtracker.ui.resources.attention_backup_never
import ch.qscqlmpa.wingtracker.ui.resources.attention_backup_title
import ch.qscqlmpa.wingtracker.ui.resources.attention_due_soon
import ch.qscqlmpa.wingtracker.ui.resources.attention_open
import ch.qscqlmpa.wingtracker.ui.resources.attention_title
import ch.qscqlmpa.wingtracker.ui.resources.backup_create
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_planned_on
import ch.qscqlmpa.wingtracker.ui.theme.extraColors
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.stringResource

/** The panel's border takes the colour of the most urgent thing in it, so its importance shows before it is read. */
@Composable
private fun panelAccent(attention: Attention): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        attention.dueCount > 0 -> scheme.error
        attention.dueSoonCount > 0 || attention.backup != null -> MaterialTheme.extraColors.warning
        else -> scheme.primary
    }
}

/** Sits above the logbook only while something needs attention, so the logbook stays the home screen. */
@Composable
fun AttentionPanel(
    attention: Attention,
    onOpen: (GearRef) -> Unit,
    backup: BackupPresenter,
) {
    if (attention.isEmpty) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(PANEL_BORDER, panelAccent(attention)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.attention_title), style = MaterialTheme.typography.titleMedium)
            attention.items.forEach { AttentionRow(it, onOpen = { onOpen(it.ref) }) }
            attention.backup?.let { BackupRow(it, backup) }
        }
    }
}

private val PANEL_BORDER = 1.5.dp

@Composable
private fun stateColor(state: DueState): Color =
    when (state) {
        DueState.Due -> MaterialTheme.colorScheme.error
        DueState.DueSoon -> MaterialTheme.extraColors.warning
        DueState.Ok -> MaterialTheme.colorScheme.primary
    }

@Composable
private fun AttentionRow(
    item: AttentionItem,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The icon repeats the meaning of the colour, so it never depends on colour alone.
        val icon = if (item.state == DueState.Ok) Icons.Default.DateRange else item.state.severity().icon()
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = stateColor(item.state),
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall)
            val detail = item.status?.takeIf { item.state != DueState.Ok }?.chipText()
            // "Due soon" is said outright; "due" is already the whole message of the status text.
            val statusText =
                if (item.state == DueState.DueSoon && detail != null) {
                    "${stringResource(Res.string.attention_due_soon)} · $detail"
                } else {
                    detail
                }
            val plannedText =
                item.nextPlanned?.let {
                    stringResource(
                        Res.string.maintenance_planned_on,
                        it.date.toDisplay(),
                    )
                }
            listOfNotNull(statusText, plannedText).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onOpen) { Text(stringResource(Res.string.attention_open)) }
    }
}

/** Backs up right here, since the reminder is solved by one action and the pilot need not look for it. */
@Composable
private fun BackupRow(
    due: BackupDue,
    presenter: BackupPresenter,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Severity.Warning.icon()?.let {
            Icon(
                it,
                contentDescription = null,
                tint = MaterialTheme.extraColors.warning,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.attention_backup_title), style = MaterialTheme.typography.titleSmall)
            Text(
                due.lastMadeOn?.let { stringResource(Res.string.attention_backup_last, it.toDisplay()) }
                    ?: stringResource(Res.string.attention_backup_never),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            presenter.failure?.let {
                Text(it.message(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (presenter.busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        TextButton(onClick = presenter::create, enabled = !presenter.busy) {
            Text(stringResource(Res.string.backup_create))
        }
    }
}
