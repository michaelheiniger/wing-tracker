package ch.qscqlmpa.wingtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_dismiss
import ch.qscqlmpa.wingtracker.ui.resources.file_names_limited
import ch.qscqlmpa.wingtracker.ui.theme.extraColors
import org.jetbrains.compose.resources.stringResource

/** Warns up front that some files cannot be opened on this system, before a file dialog fails for no clear reason. */
@Composable
fun FileNamesBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.extraColors
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = colors.warningContainer,
                contentColor = colors.onWarningContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Text(stringResource(Res.string.file_names_limited), modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_dismiss)) }
        }
    }
}
