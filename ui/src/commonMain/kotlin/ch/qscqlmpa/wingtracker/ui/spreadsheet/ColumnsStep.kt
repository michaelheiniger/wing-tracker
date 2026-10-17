package ch.qscqlmpa.wingtracker.ui.spreadsheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import ch.qscqlmpa.wingtracker.flights.spreadsheet.FlightField
import ch.qscqlmpa.wingtracker.flights.spreadsheet.RowProblem
import ch.qscqlmpa.wingtracker.flights.spreadsheet.RowProblemKind
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SheetReading
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetFlight
import ch.qscqlmpa.wingtracker.ui.components.AppDropdownMenu
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.flights.importing.Busy
import ch.qscqlmpa.wingtracker.ui.flights.importing.Buttons
import ch.qscqlmpa.wingtracker.ui.flights.importing.Hint
import ch.qscqlmpa.wingtracker.ui.flights.importing.IMPORT_DIALOG_WIDTH_DP
import ch.qscqlmpa.wingtracker.ui.flights.importing.ImportDone
import ch.qscqlmpa.wingtracker.ui.flights.importing.LeaveButtons
import ch.qscqlmpa.wingtracker.ui.flights.importing.StepActions
import ch.qscqlmpa.wingtracker.ui.flights.importing.WingGroupList
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.import_back
import ch.qscqlmpa.wingtracker.ui.resources.import_count
import ch.qscqlmpa.wingtracker.ui.resources.import_nothing_new
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_before_tracking
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_choose_other
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_column
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_column_none
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_columns_hint
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_columns_title
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_duplicates
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_duration_hint
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_field_date
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_field_distance
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_field_hours
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_field_landing
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_field_minutes
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_field_notes
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_field_takeoff
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_field_wing
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_flights_found
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_ignored
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_incomplete
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_next
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_preview_row
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_preview_title
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_problem_missing_date
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_problem_missing_duration
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_problem_missing_wing
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_problem_row
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_problem_unreadable_date
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_problem_unreadable_distance
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_problem_unreadable_duration
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_problems
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_problems_left_out
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_problems_more
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_reading
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_sheet
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_sites
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_title
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_wings_hint
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val PREVIEW_ROWS = 3
private const val PROBLEM_ROWS = 5
private const val STEP_MAX_HEIGHT_DP = 560
private const val LETTERS = 26

/** Which column holds what, with the flights the choice gives, so a wrong choice shows before anything is saved. */
@Composable
internal fun ColumnsStep(
    presenter: SpreadsheetImportPresenter,
    actions: StepActions,
) {
    val opened = checkNotNull(presenter.opened)
    val reading = presenter.reading
    Column(
        modifier = Modifier.heightIn(max = STEP_MAX_HEIGHT_DP.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Most files hold one sheet; asking about it then would only be noise.
        if (opened.sheets.size > 1) {
            LabelledChoice(
                label = stringResource(Res.string.spreadsheet_sheet),
                selected = opened.sheets[presenter.sheetIndex].sheet.name,
                options = opened.sheets.map { it.sheet.name },
                onSelect = { presenter.selectSheet(it) },
            )
        }
        Text(stringResource(Res.string.spreadsheet_columns_title), style = MaterialTheme.typography.titleMedium)
        Hint(stringResource(Res.string.spreadsheet_columns_hint))
        val headers = reading?.headers.orEmpty()
        val columnLabels = headers.mapIndexed { index, header -> columnLabel(index, header) }
        FlightField.entries.forEach { field ->
            val column = presenter.columns.columnOf(field)
            LabelledChoice(
                label = stringResource(field.label()),
                selected =
                    column?.let { columnLabels.getOrNull(it) } ?: stringResource(Res.string.spreadsheet_column_none),
                options = listOf(stringResource(Res.string.spreadsheet_column_none)) + columnLabels,
                onSelect = { presenter.assign(field, if (it == 0) null else it - 1) },
            )
            if (field == FlightField.DurationMinutes) Hint(stringResource(Res.string.spreadsheet_duration_hint))
        }
        IgnoredColumns(
            headers,
            presenter.columns.columns.values
                .toSet(),
        )
        if (!presenter.columns.isComplete) {
            Text(stringResource(Res.string.spreadsheet_incomplete), color = MaterialTheme.colorScheme.error)
        } else if (reading != null) {
            Preview(reading)
        }
    }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    Buttons {
        LeaveButtons(actions)
        Button(onClick = presenter::next, enabled = presenter.canContinue) {
            Text(stringResource(Res.string.spreadsheet_next))
        }
    }
}

/** Says which columns stay behind, so nothing is dropped without the pilot knowing. */
@Composable
private fun IgnoredColumns(
    headers: List<String>,
    used: Set<Int>,
) {
    val ignored = headers.withIndex().filter { (index, header) -> header.isNotBlank() && index !in used }
    if (ignored.isNotEmpty()) {
        Hint(stringResource(Res.string.spreadsheet_ignored, ignored.joinToString(", ") { it.value }))
    }
}

/** What the current columns make of the sheet, so a wrong choice shows before anything is imported. */
@Composable
private fun Preview(reading: SheetReading) {
    Text(
        pluralStringResource(Res.plurals.spreadsheet_flights_found, reading.flights.size, reading.flights.size),
        style = MaterialTheme.typography.titleSmall,
    )
    if (reading.flights.isNotEmpty()) {
        Hint(stringResource(Res.string.spreadsheet_preview_title))
        reading.flights.take(PREVIEW_ROWS).forEach { Text(it.summary(), style = MaterialTheme.typography.bodySmall) }
    }
    if (reading.problems.isNotEmpty()) {
        Text(
            pluralStringResource(Res.plurals.spreadsheet_problems, reading.problems.size, reading.problems.size),
            color = MaterialTheme.colorScheme.error,
        )
        reading.problems.take(PROBLEM_ROWS).forEach { problem ->
            Text(problem.describe(), style = MaterialTheme.typography.bodySmall)
        }
        val more = reading.problems.size - PROBLEM_ROWS
        if (more > 0) Hint(stringResource(Res.string.spreadsheet_problems_more, more))
    }
}

@Composable
private fun SpreadsheetFlight.summary(): String {
    val sites =
        listOfNotNull(takeOffSite, landingSite)
            .takeIf { it.size == 2 }
            ?.let { (from, to) -> stringResource(Res.string.spreadsheet_sites, from, to) }
            ?: takeOffSite
            ?: landingSite
    val main = stringResource(Res.string.spreadsheet_preview_row, date.toDisplay(), wing.name, formatDuration(duration))
    return listOfNotNull(main, sites).joinToString(" · ")
}

@Composable
private fun RowProblem.describe(): String {
    val reason =
        when (kind) {
            RowProblemKind.MissingDate -> Res.string.spreadsheet_problem_missing_date
            RowProblemKind.UnreadableDate -> Res.string.spreadsheet_problem_unreadable_date
            RowProblemKind.MissingWing -> Res.string.spreadsheet_problem_missing_wing
            RowProblemKind.MissingDuration -> Res.string.spreadsheet_problem_missing_duration
            RowProblemKind.UnreadableDuration -> Res.string.spreadsheet_problem_unreadable_duration
            RowProblemKind.UnreadableDistance -> Res.string.spreadsheet_problem_unreadable_distance
        }
    return stringResource(Res.string.spreadsheet_problem_row, rowNumber, stringResource(reason))
}

private fun FlightField.label(): StringResource =
    when (this) {
        FlightField.Date -> Res.string.spreadsheet_field_date
        FlightField.Wing -> Res.string.spreadsheet_field_wing
        FlightField.DurationHours -> Res.string.spreadsheet_field_hours
        FlightField.DurationMinutes -> Res.string.spreadsheet_field_minutes
        FlightField.TakeOff -> Res.string.spreadsheet_field_takeoff
        FlightField.Landing -> Res.string.spreadsheet_field_landing
        FlightField.Distance -> Res.string.spreadsheet_field_distance
        FlightField.Notes -> Res.string.spreadsheet_field_notes
    }

/** Columns are named by letter as the spreadsheet shows them, with the header to recognise them by. */
@Composable
private fun columnLabel(
    index: Int,
    header: String,
): String {
    val letter = columnLetter(index)
    return if (header.isBlank()) letter else stringResource(Res.string.spreadsheet_column, letter, header)
}

internal fun columnLetter(index: Int): String {
    var rest = index + 1
    val letters = StringBuilder()
    while (rest > 0) {
        val digit = (rest - 1) % LETTERS
        letters.insert(0, 'A' + digit)
        rest = (rest - 1) / LETTERS
    }
    return letters.toString()
}

@Composable
private fun LabelledChoice(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Box(modifier = Modifier.weight(2f)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelect(index)
                        },
                    )
                }
            }
        }
    }
}
