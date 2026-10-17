package ch.qscqlmpa.wingtracker.ui.flights.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import ch.qscqlmpa.wingtracker.flights.importing.ImportFailure
import ch.qscqlmpa.wingtracker.flights.importing.ImportResult
import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import ch.qscqlmpa.wingtracker.flights.importing.WingGroup
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.flights.wingLabel
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.action_close
import ch.qscqlmpa.wingtracker.ui.resources.import_back
import ch.qscqlmpa.wingtracker.ui.resources.import_choice_create
import ch.qscqlmpa.wingtracker.ui.resources.import_choice_new
import ch.qscqlmpa.wingtracker.ui.resources.import_choice_skip
import ch.qscqlmpa.wingtracker.ui.resources.import_done_summary
import ch.qscqlmpa.wingtracker.ui.resources.import_done_title
import ch.qscqlmpa.wingtracker.ui.resources.import_edit_new
import ch.qscqlmpa.wingtracker.ui.resources.import_failed_row
import ch.qscqlmpa.wingtracker.ui.resources.import_failed_title
import ch.qscqlmpa.wingtracker.ui.resources.import_failure_future
import ch.qscqlmpa.wingtracker.ui.resources.import_failure_save
import ch.qscqlmpa.wingtracker.ui.resources.import_new_incomplete
import ch.qscqlmpa.wingtracker.ui.resources.import_wing_flights
import ch.qscqlmpa.wingtracker.ui.resources.import_wing_name
import ch.qscqlmpa.wingtracker.ui.resources.import_wing_period
import ch.qscqlmpa.wingtracker.ui.resources.import_wings_title
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// Shared by every import, so they all look and behave the same.

internal const val IMPORT_DIALOG_WIDTH_DP = 720
internal const val IMPORT_LIST_MAX_HEIGHT_DP = 380

/** What a step can do in the import wizard besides its own work. [back] is null when the step cannot go back. */
internal class StepActions(
    val back: (() -> Unit)?,
    val cancel: () -> Unit,
    val close: () -> Unit,
)

/** Back to the sources when the wizard allows it, and Cancel; placed before a step's own buttons. */
@Composable
internal fun LeaveButtons(actions: StepActions) {
    actions.back?.let { TextButton(onClick = it) { Text(stringResource(Res.string.import_back)) } }
    TextButton(onClick = actions.cancel) { Text(stringResource(Res.string.action_cancel)) }
}

@Composable
internal fun Busy(text: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        text?.let { Text(it) }
    }
}

@Composable
internal fun Buttons(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        content()
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One row per source wing, where the pilot says which wing in the app it is; [extra] adds source-specific notes. */
@Composable
internal fun <G : WingGroup<*>> WingGroupList(
    groups: List<G>,
    mapping: WingMapping,
    existing: List<Wing>,
    hint: String,
    extra: @Composable ColumnScope.(G) -> Unit = {},
) {
    Text(stringResource(Res.string.import_wings_title), style = MaterialTheme.typography.titleMedium)
    Hint(hint)
    LazyColumn(modifier = Modifier.heightIn(max = IMPORT_LIST_MAX_HEIGHT_DP.dp)) {
        items(groups) { group ->
            GroupRow(group, mapping, existing, extra)
            HorizontalDivider()
        }
    }
}

@Composable
private fun <G : WingGroup<*>> GroupRow(
    group: G,
    mapping: WingMapping,
    existing: List<Wing>,
    extra: @Composable ColumnScope.(G) -> Unit,
) {
    var creating by remember { mutableStateOf<CreateRequest?>(null) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(group.wing.label(), style = MaterialTheme.typography.titleSmall)
            Text(
                pluralStringResource(Res.plurals.import_wing_flights, group.flights.size, group.flights.size) +
                    " · " +
                    stringResource(
                        Res.string.import_wing_period,
                        group.firstDate.toDisplay(),
                        group.lastDate.toDisplay(),
                        formatDuration(group.totalDuration),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (mapping.choiceOf(group) is WingChoice.New && !mapping.isReady(group)) {
                StatusBadge(stringResource(Res.string.import_new_incomplete), Severity.Warning)
            }
            extra(group)
        }
        ChoiceMenu(group, mapping, existing, onCreate = { creating = CreateRequest(null) })
        val choice = mapping.choiceOf(group)
        if (choice is WingChoice.New) {
            TextButton(onClick = { creating = CreateRequest(choice.key) }) {
                Text(stringResource(Res.string.import_edit_new))
            }
        }
    }
    creating?.let { request ->
        val initial = request.key?.let { mapping.newWings[it] } ?: mapping.suggestedSpec(group.wing)
        NewWingDialog(
            initial = initial,
            onConfirm = { spec ->
                if (request.key == null) mapping.createWingFor(group, spec) else mapping.editNewWing(request.key, spec)
                creating = null
            },
            onDismiss = { creating = null },
        )
    }
}

private class CreateRequest(
    val key: String?,
)

@Composable
private fun ChoiceMenu(
    group: WingGroup<*>,
    mapping: WingMapping,
    existing: List<Wing>,
    onCreate: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }) { Text(mapping.choiceLabel(group, existing)) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        existing.forEach { wing ->
            DropdownMenuItem(
                text = { Text(wingLabel(wing)) },
                onClick = {
                    mapping.choose(group, WingChoice.Existing(wing.id))
                    expanded = false
                },
            )
        }
        // Wings defined for other source wings can be picked again, so one new wing can take several of them.
        mapping.newWings.forEach { (key, spec) ->
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.import_choice_new, spec.name)) },
                onClick = {
                    mapping.choose(group, WingChoice.New(key))
                    expanded = false
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.import_choice_create)) },
            onClick = {
                expanded = false
                onCreate()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.import_choice_skip)) },
            onClick = {
                mapping.choose(group, WingChoice.Skip)
                expanded = false
            },
        )
    }
}

@Composable
private fun WingMapping.choiceLabel(
    group: WingGroup<*>,
    existing: List<Wing>,
): String =
    when (val choice = choiceOf(group)) {
        is WingChoice.Existing -> existing.firstOrNull { it.id == choice.wingId }?.let { wingLabel(it) }.orEmpty()
        is WingChoice.New -> stringResource(Res.string.import_choice_new, newWings[choice.key]?.name.orEmpty())
        WingChoice.Skip -> stringResource(Res.string.import_choice_skip)
    }

@Composable
private fun SourceWing.label(): String =
    if (brand == null) name else stringResource(Res.string.import_wing_name, name, brand.orEmpty())

/** What the import did, with the flights it could not bring in; [extra] adds source-specific lines. */
@Composable
internal fun ImportDone(
    result: ImportResult<*>?,
    onClose: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    result ?: return
    Text(stringResource(Res.string.import_done_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(Res.string.import_done_summary, result.imported, result.createdWings.size, result.skipped))
    extra()
    if (result.failed.isNotEmpty()) {
        Text(stringResource(Res.string.import_failed_title), color = MaterialTheme.colorScheme.error)
        LazyColumn(modifier = Modifier.heightIn(max = IMPORT_LIST_MAX_HEIGHT_DP.dp)) {
            items(result.failed) { failed ->
                val reason =
                    when (failed.failure) {
                        ImportFailure.DateInFuture -> {
                            stringResource(Res.string.import_failure_future)
                        }

                        ImportFailure.SaveFailed -> {
                            stringResource(
                                Res.string.import_failure_save,
                                failed.detail.orEmpty(),
                            )
                        }
                    }
                Text(
                    stringResource(Res.string.import_failed_row, failed.flight.date.toDisplay(), reason),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    Buttons { Button(onClick = onClose) { Text(stringResource(Res.string.action_close)) } }
}
