package ch.qscqlmpa.wingtracker.ui.syride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import ch.qscqlmpa.wingtracker.flights.syride.NewWingSpec
import ch.qscqlmpa.wingtracker.flights.syride.SyrideFailure
import ch.qscqlmpa.wingtracker.flights.syride.SyrideImportResult
import ch.qscqlmpa.wingtracker.flights.syride.SyrideWing
import ch.qscqlmpa.wingtracker.flights.syride.SyrideWingGroup
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.flights.wingLabel
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.action_close
import ch.qscqlmpa.wingtracker.ui.resources.action_save
import ch.qscqlmpa.wingtracker.ui.resources.field_manufacturer
import ch.qscqlmpa.wingtracker.ui.resources.field_model
import ch.qscqlmpa.wingtracker.ui.resources.field_name
import ch.qscqlmpa.wingtracker.ui.resources.syride_account_hint
import ch.qscqlmpa.wingtracker.ui.resources.syride_already
import ch.qscqlmpa.wingtracker.ui.resources.syride_back
import ch.qscqlmpa.wingtracker.ui.resources.syride_choice_create
import ch.qscqlmpa.wingtracker.ui.resources.syride_choice_new
import ch.qscqlmpa.wingtracker.ui.resources.syride_choice_skip
import ch.qscqlmpa.wingtracker.ui.resources.syride_done_summary
import ch.qscqlmpa.wingtracker.ui.resources.syride_done_title
import ch.qscqlmpa.wingtracker.ui.resources.syride_edit_new
import ch.qscqlmpa.wingtracker.ui.resources.syride_failed_row
import ch.qscqlmpa.wingtracker.ui.resources.syride_failed_title
import ch.qscqlmpa.wingtracker.ui.resources.syride_failure_future
import ch.qscqlmpa.wingtracker.ui.resources.syride_failure_save
import ch.qscqlmpa.wingtracker.ui.resources.syride_import_count
import ch.qscqlmpa.wingtracker.ui.resources.syride_new_incomplete
import ch.qscqlmpa.wingtracker.ui.resources.syride_newwing_hint
import ch.qscqlmpa.wingtracker.ui.resources.syride_newwing_title
import ch.qscqlmpa.wingtracker.ui.resources.syride_nothing_new
import ch.qscqlmpa.wingtracker.ui.resources.syride_read
import ch.qscqlmpa.wingtracker.ui.resources.syride_reading
import ch.qscqlmpa.wingtracker.ui.resources.syride_title
import ch.qscqlmpa.wingtracker.ui.resources.syride_username
import ch.qscqlmpa.wingtracker.ui.resources.syride_wing_flights
import ch.qscqlmpa.wingtracker.ui.resources.syride_wing_name
import ch.qscqlmpa.wingtracker.ui.resources.syride_wing_period
import ch.qscqlmpa.wingtracker.ui.resources.syride_wings_hint
import ch.qscqlmpa.wingtracker.ui.resources.syride_wings_title
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val DIALOG_WIDTH_DP = 720
private const val LIST_MAX_HEIGHT_DP = 380

/** The import from Syride, step by step; [wings] are the wings already in the app. */
@Composable
fun SyrideImportDialog(
    presenter: SyrideImportPresenter,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = { if (presenter.stage != SyrideStage.Importing) onClose() }) {
        Card(modifier = Modifier.width(DIALOG_WIDTH_DP.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.syride_title), style = MaterialTheme.typography.headlineSmall)
                when (presenter.stage) {
                    SyrideStage.Account -> AccountStep(presenter, onClose)
                    SyrideStage.Reading -> Busy(stringResource(Res.string.syride_reading))
                    SyrideStage.Wings -> WingsStep(presenter, onClose)
                    SyrideStage.Importing -> Busy(null)
                    SyrideStage.Done -> DoneStep(presenter.result, onClose)
                }
            }
        }
    }
}

@Composable
private fun Busy(text: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        text?.let { Text(it) }
    }
}

@Composable
private fun AccountStep(
    presenter: SyrideImportPresenter,
    onClose: () -> Unit,
) {
    Text(
        stringResource(Res.string.syride_account_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = presenter.username,
        onValueChange = presenter::changeUsername,
        label = { Text(stringResource(Res.string.syride_username)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    Buttons {
        TextButton(onClick = onClose) { Text(stringResource(Res.string.action_cancel)) }
        Button(onClick = presenter::read, enabled = presenter.canRead) { Text(stringResource(Res.string.syride_read)) }
    }
}

@Composable
private fun WingsStep(
    presenter: SyrideImportPresenter,
    onClose: () -> Unit,
) {
    val draft = checkNotNull(presenter.draft)
    if (draft.alreadyImported > 0) {
        Text(
            stringResource(Res.string.syride_already, draft.alreadyImported),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (draft.groups.isEmpty()) {
        Text(stringResource(Res.string.syride_nothing_new))
    } else {
        Text(stringResource(Res.string.syride_wings_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.syride_wings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT_DP.dp)) {
            items(draft.groups) { group ->
                GroupRow(group, presenter, draft.existingWings)
                HorizontalDivider()
            }
        }
    }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    Buttons {
        TextButton(onClick = presenter::back) { Text(stringResource(Res.string.syride_back)) }
        TextButton(onClick = onClose) { Text(stringResource(Res.string.action_cancel)) }
        Button(
            onClick = { presenter.import(onFinished = {}) },
            enabled = presenter.importableCount > 0 && presenter.allReady,
        ) {
            Text(
                pluralStringResource(
                    Res.plurals.syride_import_count,
                    presenter.importableCount,
                    presenter.importableCount,
                ),
            )
        }
    }
}

@Composable
private fun GroupRow(
    group: SyrideWingGroup,
    presenter: SyrideImportPresenter,
    existing: List<Wing>,
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
                pluralStringResource(Res.plurals.syride_wing_flights, group.flights.size, group.flights.size) +
                    " · " +
                    stringResource(
                        Res.string.syride_wing_period,
                        group.firstDate.toDisplay(),
                        group.lastDate.toDisplay(),
                        formatDuration(group.totalDuration),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (presenter.mapping.choiceOf(group) is WingChoice.New && !presenter.mapping.isReady(group)) {
                StatusBadge(stringResource(Res.string.syride_new_incomplete), Severity.Warning)
            }
        }
        ChoiceMenu(group, presenter, existing, onCreate = { creating = CreateRequest(null) })
        val choice = presenter.mapping.choiceOf(group)
        if (choice is WingChoice.New) {
            TextButton(onClick = { creating = CreateRequest(choice.key) }) {
                Text(stringResource(Res.string.syride_edit_new))
            }
        }
    }
    creating?.let { request ->
        val initial = request.key?.let { presenter.mapping.newWings[it] } ?: presenter.mapping.suggestedSpec(group.wing)
        NewWingDialog(
            initial = initial,
            onConfirm = { spec ->
                if (request.key ==
                    null
                ) {
                    presenter.mapping.createWingFor(group, spec)
                } else {
                    presenter.mapping.editNewWing(request.key, spec)
                }
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
    group: SyrideWingGroup,
    presenter: SyrideImportPresenter,
    existing: List<Wing>,
    onCreate: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }) { Text(presenter.choiceLabel(group, existing)) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        existing.forEach { wing ->
            DropdownMenuItem(
                text = { Text(wingLabel(wing)) },
                onClick = {
                    presenter.mapping.choose(group, WingChoice.Existing(wing.id))
                    expanded = false
                },
            )
        }
        // Wings defined for other Syride wings can be picked again, so one new wing can take several of them.
        presenter.mapping.newWings.forEach { (key, spec) ->
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.syride_choice_new, spec.name)) },
                onClick = {
                    presenter.mapping.choose(group, WingChoice.New(key))
                    expanded = false
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.syride_choice_create)) },
            onClick = {
                expanded = false
                onCreate()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.syride_choice_skip)) },
            onClick = {
                presenter.mapping.choose(group, WingChoice.Skip)
                expanded = false
            },
        )
    }
}

@Composable
private fun SyrideImportPresenter.choiceLabel(
    group: SyrideWingGroup,
    existing: List<Wing>,
): String =
    when (val choice = mapping.choiceOf(group)) {
        is WingChoice.Existing -> existing.firstOrNull { it.id == choice.wingId }?.let { wingLabel(it) }.orEmpty()
        is WingChoice.New -> stringResource(Res.string.syride_choice_new, mapping.newWings[choice.key]?.name.orEmpty())
        WingChoice.Skip -> stringResource(Res.string.syride_choice_skip)
    }

@Composable
private fun SyrideWing.label(): String =
    if (brand == null) name else stringResource(Res.string.syride_wing_name, name, brand.orEmpty())

@Composable
private fun DoneStep(
    result: SyrideImportResult?,
    onClose: () -> Unit,
) {
    result ?: return
    Text(stringResource(Res.string.syride_done_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(Res.string.syride_done_summary, result.imported, result.createdWings.size, result.skipped))
    if (result.failed.isNotEmpty()) {
        Text(stringResource(Res.string.syride_failed_title), color = MaterialTheme.colorScheme.error)
        LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT_DP.dp)) {
            items(result.failed) { failed ->
                val reason =
                    when (failed.failure) {
                        SyrideFailure.DateInFuture -> {
                            stringResource(Res.string.syride_failure_future)
                        }

                        SyrideFailure.SaveFailed -> {
                            stringResource(
                                Res.string.syride_failure_save,
                                failed.detail.orEmpty(),
                            )
                        }
                    }
                Text(
                    stringResource(Res.string.syride_failed_row, failed.flight.date.toDisplay(), reason),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    Buttons { Button(onClick = onClose) { Text(stringResource(Res.string.action_close)) } }
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
