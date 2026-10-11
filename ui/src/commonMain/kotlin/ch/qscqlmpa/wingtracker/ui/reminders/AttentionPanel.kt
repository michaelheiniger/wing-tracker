package ch.qscqlmpa.wingtracker.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.gear.maintenance.DueState
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.reminders.Attention
import ch.qscqlmpa.wingtracker.reminders.AttentionItem
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.chipText
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.attention_due_soon
import ch.qscqlmpa.wingtracker.ui.resources.attention_open
import ch.qscqlmpa.wingtracker.ui.resources.attention_title
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_planned_on
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.stringResource

/** Sits above the logbook only while something needs attention, so the logbook stays the home screen. */
@Composable
fun AttentionPanel(
    attention: Attention,
    onOpen: (GearRef) -> Unit,
) {
    if (attention.isEmpty) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.attention_title), style = MaterialTheme.typography.titleMedium)
            attention.items.forEach { AttentionRow(it, onOpen = { onOpen(it.ref) }) }
        }
    }
}

@Composable
private fun AttentionRow(
    item: AttentionItem,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
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
            // Words come from the shared status text so the panel, the lists and the screen always agree.
            listOfNotNull(statusText, plannedText).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                val color =
                    if (item.state ==
                        DueState.Due
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                Text(it, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
        TextButton(onClick = onOpen) { Text(stringResource(Res.string.attention_open)) }
    }
}
