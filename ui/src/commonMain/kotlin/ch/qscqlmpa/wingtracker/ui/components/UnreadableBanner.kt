package ch.qscqlmpa.wingtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_dismiss
import ch.qscqlmpa.wingtracker.ui.resources.unreadable_kept
import ch.qscqlmpa.wingtracker.ui.resources.unreadable_quarantined
import org.jetbrains.compose.resources.stringResource

/** Tells the user a stored file could not be read, and whether it was set aside (ADR 0015). */
@Composable
fun UnreadableBanner(
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
