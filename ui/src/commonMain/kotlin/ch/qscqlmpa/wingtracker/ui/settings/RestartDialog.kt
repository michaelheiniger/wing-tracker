package ch.qscqlmpa.wingtracker.ui.settings

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ch.qscqlmpa.wingtracker.ui.components.AppAlertDialog
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.data_restart_now
import ch.qscqlmpa.wingtracker.ui.resources.data_restart_title
import org.jetbrains.compose.resources.stringResource

/** Not dismissible: carrying on would write to the old data folder while the new one is already chosen (ADR 0020). */
@Composable
fun RestartDialog(
    body: String,
    onRestart: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.data_restart_title)) },
        text = { Text(body) },
        confirmButton = { Button(onClick = onRestart) { Text(stringResource(Res.string.data_restart_now)) } },
    )
}
