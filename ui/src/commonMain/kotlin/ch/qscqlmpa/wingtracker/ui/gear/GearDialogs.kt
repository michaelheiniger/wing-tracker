package ch.qscqlmpa.wingtracker.ui.gear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.components.AppAlertDialog
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.delete_body
import ch.qscqlmpa.wingtracker.ui.resources.delete_confirm
import ch.qscqlmpa.wingtracker.ui.resources.delete_title
import ch.qscqlmpa.wingtracker.ui.resources.retire_body
import ch.qscqlmpa.wingtracker.ui.resources.retire_reason_label
import ch.qscqlmpa.wingtracker.ui.resources.retire_title
import ch.qscqlmpa.wingtracker.ui.resources.wing_retire
import org.jetbrains.compose.resources.stringResource

/** Shared by every kind of gear: retiring keeps history, deleting is for mistakes. */
@Composable
fun RetireDialog(
    name: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.retire_title, name)) },
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
fun DeleteDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_title, name)) },
        text = { Text(stringResource(Res.string.delete_body)) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(Res.string.delete_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
