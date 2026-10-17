package ch.qscqlmpa.wingtracker.ui.gear.maintenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.ui.components.AppDialog
import ch.qscqlmpa.wingtracker.ui.components.DateField
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.action_save
import ch.qscqlmpa.wingtracker.ui.resources.check_add_reports
import ch.qscqlmpa.wingtracker.ui.resources.check_form_title_complete
import ch.qscqlmpa.wingtracker.ui.resources.check_form_title_edit
import ch.qscqlmpa.wingtracker.ui.resources.check_form_title_record
import ch.qscqlmpa.wingtracker.ui.resources.check_no_reports
import ch.qscqlmpa.wingtracker.ui.resources.check_remove_file
import ch.qscqlmpa.wingtracker.ui.resources.check_reports
import ch.qscqlmpa.wingtracker.ui.resources.check_restarts_interval
import ch.qscqlmpa.wingtracker.ui.resources.field_check_comment
import ch.qscqlmpa.wingtracker.ui.resources.field_check_date
import ch.qscqlmpa.wingtracker.ui.resources.field_check_notes
import ch.qscqlmpa.wingtracker.ui.resources.field_check_type
import ch.qscqlmpa.wingtracker.ui.resources.field_company
import ch.qscqlmpa.wingtracker.ui.resources.field_plan_date
import ch.qscqlmpa.wingtracker.ui.resources.plan_form_title_add
import ch.qscqlmpa.wingtracker.ui.resources.plan_form_title_edit
import org.jetbrains.compose.resources.stringResource

/** Company text with the companies already used offered as one-click suggestions, so spellings do not drift. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanyField(
    value: String,
    suggestions: List<String>,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(Res.string.field_company)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val matching =
        suggestions
            .filter {
                it != value &&
                    it.contains(
                        value.trim(),
                        ignoreCase = true,
                    )
            }.take(MAX_SUGGESTIONS)
    if (matching.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            matching.forEach { AssistChip(onClick = { onChange(it) }, label = { Text(it) }) }
        }
    }
}

@Composable
private fun TypeChips(
    types: List<CheckType>,
    selected: CheckType,
    onSelected: (CheckType) -> Unit,
) {
    Text(stringResource(Res.string.field_check_type), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { type ->
            FilterChip(selected = selected == type, onClick = { onSelected(type) }, label = { Text(type.label()) })
        }
    }
}

@Composable
fun CheckDialog(
    presenter: CheckFormPresenter,
    companies: List<String>,
    onClose: () -> Unit,
) {
    val input = presenter.input
    val title =
        when (presenter.mode) {
            CheckFormMode.Record -> Res.string.check_form_title_record
            is CheckFormMode.Edit -> Res.string.check_form_title_edit
            is CheckFormMode.Complete -> Res.string.check_form_title_complete
        }
    AppDialog(onDismissRequest = onClose) {
        Card(modifier = Modifier.width(DIALOG_WIDTH)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
                DateField(
                    label = stringResource(Res.string.field_check_date),
                    value = input.date,
                    onValueChange = { v -> presenter.change { it.copy(date = v) } },
                    errorText = presenter.errors[CheckFormField.Date]?.message(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TypeChips(presenter.kind.allowedChecks, input.type) { type -> presenter.change { it.withType(type) } }
                if (input.type == CheckType.Other) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = input.restartsInterval,
                            onCheckedChange = { v -> presenter.change { it.copy(restartsInterval = v) } },
                        )
                        Text(stringResource(Res.string.check_restarts_interval))
                    }
                }
                CompanyField(input.company, companies) { v -> presenter.change { it.copy(company = v) } }
                OutlinedTextField(
                    value = input.notes,
                    onValueChange = { v -> presenter.change { it.copy(notes = v) } },
                    label = {
                        Text(
                            stringResource(
                                if (input.type == CheckType.Other) {
                                    Res.string.field_check_comment
                                } else {
                                    Res.string.field_check_notes
                                },
                            ),
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                ReportsSection(presenter)
                presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    TextButton(onClick = onClose) { Text(stringResource(Res.string.action_cancel)) }
                    Button(onClick = { presenter.submit(onSaved = onClose) }, enabled = !presenter.saving) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportsSection(presenter: CheckFormPresenter) {
    val input = presenter.input
    Text(stringResource(Res.string.check_reports), style = MaterialTheme.typography.labelLarge)
    if (input.keptReports.isEmpty() && input.newReports.isEmpty()) {
        Text(stringResource(Res.string.check_no_reports), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        input.keptReports.forEach { report ->
            AssistChip(
                onClick = { presenter.removeKept(report) },
                label = { Text(stringResource(Res.string.check_remove_file, report.fileName)) },
            )
        }
        input.newReports.forEachIndexed { index, file ->
            AssistChip(
                onClick = { presenter.removeNew(index) },
                label = { Text(stringResource(Res.string.check_remove_file, file.fileName)) },
            )
        }
    }
    OutlinedButton(onClick = presenter::addReports) { Text(stringResource(Res.string.check_add_reports)) }
}

@Composable
fun PlanDialog(
    presenter: PlanFormPresenter,
    companies: List<String>,
    onClose: () -> Unit,
) {
    val input = presenter.input
    AppDialog(onDismissRequest = onClose) {
        Card(modifier = Modifier.width(DIALOG_WIDTH)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(
                        if (presenter.editing ==
                            null
                        ) {
                            Res.string.plan_form_title_add
                        } else {
                            Res.string.plan_form_title_edit
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
                DateField(
                    label = stringResource(Res.string.field_plan_date),
                    value = input.date,
                    onValueChange = { v -> presenter.change { it.copy(date = v) } },
                    errorText = presenter.errors[CheckFormField.Date]?.message(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TypeChips(
                    presenter.kind.allowedChecks,
                    input.type,
                ) { type -> presenter.change { it.copy(type = type) } }
                CompanyField(input.company, companies) { v -> presenter.change { it.copy(company = v) } }
                OutlinedTextField(
                    value = input.notes,
                    onValueChange = { v -> presenter.change { it.copy(notes = v) } },
                    label = { Text(stringResource(Res.string.field_check_notes)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    TextButton(onClick = onClose) { Text(stringResource(Res.string.action_cancel)) }
                    Button(onClick = { presenter.submit(onSaved = onClose) }, enabled = !presenter.saving) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        }
    }
}

private val DIALOG_WIDTH = 560.dp
private const val MAX_SUGGESTIONS = 4
