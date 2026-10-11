package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.ui.components.DateField
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.action_save
import ch.qscqlmpa.wingtracker.ui.resources.field_flight_date
import ch.qscqlmpa.wingtracker.ui.resources.field_flight_duration
import ch.qscqlmpa.wingtracker.ui.resources.field_flight_wing
import ch.qscqlmpa.wingtracker.ui.resources.field_landing_site
import ch.qscqlmpa.wingtracker.ui.resources.field_notes
import ch.qscqlmpa.wingtracker.ui.resources.field_takeoff_site
import ch.qscqlmpa.wingtracker.ui.resources.flight_form_title_add
import ch.qscqlmpa.wingtracker.ui.resources.flight_form_title_edit
import ch.qscqlmpa.wingtracker.ui.resources.flight_unknown_wing
import ch.qscqlmpa.wingtracker.ui.resources.flight_wing_retired
import ch.qscqlmpa.wingtracker.ui.resources.igc_detected
import ch.qscqlmpa.wingtracker.ui.resources.igc_dialog_title
import ch.qscqlmpa.wingtracker.ui.resources.igc_duplicate
import ch.qscqlmpa.wingtracker.ui.resources.igc_duplicate_confirm
import ch.qscqlmpa.wingtracker.ui.resources.igc_file
import ch.qscqlmpa.wingtracker.ui.resources.igc_none_detected
import ch.qscqlmpa.wingtracker.ui.toDisplay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** Quick-entry dialog: focus starts on the duration, the one thing that is never pre-filled. */
@Composable
fun FlightDialog(
    presenter: FlightFormPresenter,
    wings: List<Wing>,
    onClose: () -> Unit,
) {
    val input = presenter.input
    val errors = presenter.errors
    val durationFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { durationFocus.requestFocus() }

    Dialog(onDismissRequest = onClose) {
        Card(modifier = Modifier.width(520.dp)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(
                        if (presenter.editing ==
                            null
                        ) {
                            Res.string.flight_form_title_add
                        } else {
                            Res.string.flight_form_title_edit
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )

                presenter.igc?.let { TrackInfo(it, presenter, wings) }
                DateField(
                    label = stringResource(Res.string.field_flight_date),
                    value = input.date,
                    onValueChange = { v -> presenter.change { it.copy(date = v) } },
                    errorText = errors[FlightFormField.Date]?.message(),
                    modifier = Modifier.fillMaxWidth(),
                )
                WingDropdown(
                    wings = wings,
                    selected = input.wingId?.let { id -> wings.firstOrNull { it.id == id } },
                    errorText = errors[FlightFormField.Wing]?.message(),
                    onSelected = { wing -> presenter.change { it.copy(wingId = wing.id) } },
                )
                OutlinedTextField(
                    value = input.duration,
                    onValueChange = { v -> presenter.change { it.copy(duration = v) } },
                    label = { Text(stringResource(Res.string.field_flight_duration)) },
                    isError = FlightFormField.Duration in errors,
                    supportingText = { errors[FlightFormField.Duration]?.let { Text(it.message()) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(durationFocus),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = input.takeOffSite,
                        onValueChange = { v -> presenter.change { it.copy(takeOffSite = v) } },
                        label = { Text(stringResource(Res.string.field_takeoff_site)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = input.landingSite,
                        onValueChange = { v -> presenter.change { it.copy(landingSite = v) } },
                        label = { Text(stringResource(Res.string.field_landing_site)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = input.notes,
                    onValueChange = { v -> presenter.change { it.copy(notes = v) } },
                    label = { Text(stringResource(Res.string.field_notes)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    TextButton(onClick = onClose) { Text(stringResource(Res.string.action_cancel)) }
                    Button(
                        onClick = { presenter.submit(onSaved = onClose) },
                        enabled = !presenter.saving && presenter.mayImport,
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WingDropdown(
    wings: List<Wing>,
    selected: Wing?,
    errorText: String?,
    onSelected: (Wing) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { wingLabel(it) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.field_flight_wing)) },
            isError = errorText != null,
            supportingText = { errorText?.let { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            wings.forEach { wing ->
                DropdownMenuItem(
                    text = { Text(wingLabel(wing)) },
                    onClick = {
                        onSelected(wing)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun wingLabel(wing: Wing): String =
    if (wing.isActive) wing.details.name else stringResource(Res.string.flight_wing_retired, wing.details.name)

/** What the track told us, and a warning when this exact file was imported before. */
@Composable
private fun TrackInfo(
    prepared: PreparedIgc,
    presenter: FlightFormPresenter,
    wings: List<Wing>,
) {
    val draft = prepared.draft
    Text(stringResource(Res.string.igc_file, draft.fileName), style = MaterialTheme.typography.bodySmall)
    val takeOff = draft.takeOff
    val landing = draft.landing
    if (takeOff != null && landing != null) {
        Text(
            stringResource(Res.string.igc_detected, takeOff.toUtcClock(), landing.toUtcClock()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            stringResource(Res.string.igc_none_detected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    draft.duplicateOf?.let { earlier ->
        Text(
            stringResource(
                Res.string.igc_duplicate,
                earlier.details.date.toDisplay(),
                wings.firstOrNull { it.id == earlier.details.wingId }?.details?.name
                    ?: stringResource(Res.string.flight_unknown_wing),
            ),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = presenter.importAnyway, onCheckedChange = presenter::confirmImportAnyway)
            Text(stringResource(Res.string.igc_duplicate_confirm))
        }
    }
}

private fun kotlin.time.Instant.toUtcClock(): String {
    val time = toLocalDateTime(TimeZone.UTC).time
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}
