package ch.qscqlmpa.wingtracker.ui.flights.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.window.Dialog
import ch.qscqlmpa.wingtracker.flights.importing.NewWingSpec
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.action_save
import ch.qscqlmpa.wingtracker.ui.resources.field_manufacturer
import ch.qscqlmpa.wingtracker.ui.resources.field_model
import ch.qscqlmpa.wingtracker.ui.resources.field_name
import ch.qscqlmpa.wingtracker.ui.resources.import_newwing_hint
import ch.qscqlmpa.wingtracker.ui.resources.import_newwing_title
import org.jetbrains.compose.resources.stringResource

/** The few things needed to create a wing during the import; the rest can be completed later under Gear. */
@Composable
internal fun NewWingDialog(
    initial: NewWingSpec,
    onConfirm: (NewWingSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    var spec by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(480.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.import_newwing_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(Res.string.import_newwing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = spec.name,
                    onValueChange = { spec = spec.copy(name = it) },
                    label = { Text(stringResource(Res.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = spec.manufacturer,
                    onValueChange = { spec = spec.copy(manufacturer = it) },
                    label = { Text(stringResource(Res.string.field_manufacturer)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = spec.model,
                    onValueChange = { spec = spec.copy(model = it) },
                    label = { Text(stringResource(Res.string.field_model)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Buttons {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                    Button(onClick = { onConfirm(spec.trimmed()) }, enabled = spec.isComplete()) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        }
    }
}

private fun NewWingSpec.trimmed() = NewWingSpec(name.trim(), manufacturer.trim(), model.trim())
