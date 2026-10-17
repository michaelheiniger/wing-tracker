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
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.window.Dialog
import ch.qscqlmpa.wingtracker.flights.spreadsheet.FlightField
import ch.qscqlmpa.wingtracker.flights.spreadsheet.RowProblem
import ch.qscqlmpa.wingtracker.flights.spreadsheet.RowProblemKind
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SheetReading
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetFlight
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

/** The steps of the import from a spreadsheet, shown inside the import wizard, which also opens the file chooser. */
@Composable
internal fun SpreadsheetImportSteps(
    presenter: SpreadsheetImportPresenter,
    actions: StepActions,
    onChooseAgain: () -> Unit,
) {
    when (presenter.stage) {
        SpreadsheetStage.File -> FileStep(presenter, actions, onChooseAgain)
        SpreadsheetStage.Columns -> ColumnsStep(presenter, actions)
        SpreadsheetStage.Wings -> WingsStep(presenter, actions)
        SpreadsheetStage.Importing -> Busy(null)
        SpreadsheetStage.Done -> ImportDone(presenter.result, actions.close)
    }
}

@Composable
private fun FileStep(
    presenter: SpreadsheetImportPresenter,
    actions: StepActions,
    onChooseAgain: () -> Unit,
) {
    val failure = presenter.failure
    if (failure == null) {
        Busy(stringResource(Res.string.spreadsheet_reading))
        return
    }
    Text(failure.message(), color = MaterialTheme.colorScheme.error)
    Buttons {
        LeaveButtons(actions)
        Button(onClick = onChooseAgain) {
            Text(stringResource(Res.string.spreadsheet_choose_other))
        }
    }
}

@Composable
private fun WingsStep(
    presenter: SpreadsheetImportPresenter,
    actions: StepActions,
) {
    val draft = checkNotNull(presenter.draft)
    if (draft.problems.isNotEmpty()) {
        Hint(pluralStringResource(Res.plurals.spreadsheet_problems_left_out, draft.problems.size, draft.problems.size))
    }
    if (draft.groups.isEmpty()) {
        Text(stringResource(Res.string.import_nothing_new))
    } else {
        val present = presenter.alreadyPresent
        WingGroupList(
            draft.groups,
            presenter.mapping,
            draft.existingWings,
            hint = stringResource(Res.string.spreadsheet_wings_hint),
        ) { group ->
            val duplicates = group.flights.count { it in present }
            if (duplicates > 0) {
                StatusBadge(
                    pluralStringResource(Res.plurals.spreadsheet_duplicates, duplicates, duplicates),
                    Severity.Neutral,
                )
            }
            // Flights counted by hand on the wing may be the very ones in the file; the pilot is the one who knows.
            val before = presenter.targetWing(group)?.details?.flightsBeforeTracking ?: 0
            if (before >
                0
            ) {
                StatusBadge(stringResource(Res.string.spreadsheet_before_tracking, before), Severity.Warning)
            }
        }
    }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    Buttons {
        TextButton(onClick = presenter::back) { Text(stringResource(Res.string.import_back)) }
        TextButton(onClick = actions.cancel) { Text(stringResource(Res.string.action_cancel)) }
        Button(
            onClick = { presenter.import(onFinished = {}) },
            enabled = presenter.importableCount > 0 && presenter.allReady,
        ) {
            Text(pluralStringResource(Res.plurals.import_count, presenter.importableCount, presenter.importableCount))
        }
    }
}
