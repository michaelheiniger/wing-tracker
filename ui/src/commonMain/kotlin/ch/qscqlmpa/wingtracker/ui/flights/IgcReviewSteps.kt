package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.flights.importing.Busy
import ch.qscqlmpa.wingtracker.ui.flights.importing.Buttons
import ch.qscqlmpa.wingtracker.ui.flights.importing.Hint
import ch.qscqlmpa.wingtracker.ui.flights.importing.LeaveButtons
import ch.qscqlmpa.wingtracker.ui.flights.importing.StepActions
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_close
import ch.qscqlmpa.wingtracker.ui.resources.flight_warning_before_service
import ch.qscqlmpa.wingtracker.ui.resources.igc_batch_import
import ch.qscqlmpa.wingtracker.ui.resources.igc_batch_nothing_new
import ch.qscqlmpa.wingtracker.ui.resources.igc_batch_result
import ch.qscqlmpa.wingtracker.ui.resources.igc_batch_skipped
import ch.qscqlmpa.wingtracker.ui.resources.igc_batch_wing_for_all
import ch.qscqlmpa.wingtracker.ui.resources.igc_detected
import ch.qscqlmpa.wingtracker.ui.resources.igc_done_attached
import ch.qscqlmpa.wingtracker.ui.resources.igc_done_created
import ch.qscqlmpa.wingtracker.ui.resources.igc_done_left_out
import ch.qscqlmpa.wingtracker.ui.resources.igc_problem_duplicate
import ch.qscqlmpa.wingtracker.ui.resources.igc_problem_future
import ch.qscqlmpa.wingtracker.ui.resources.igc_problem_has_track
import ch.qscqlmpa.wingtracker.ui.resources.igc_problem_no_flight
import ch.qscqlmpa.wingtracker.ui.resources.igc_problem_no_match
import ch.qscqlmpa.wingtracker.ui.resources.igc_problem_repeated
import ch.qscqlmpa.wingtracker.ui.resources.igc_problem_save
import ch.qscqlmpa.wingtracker.ui.resources.igc_problem_unreadable
import ch.qscqlmpa.wingtracker.ui.resources.import_done_title
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val ROWS_MAX_HEIGHT_DP = 360

/** Review of the tracks about to be imported: tick the ones to import, and say on which wing each was flown. */
@Composable
internal fun IgcReviewStep(
    presenter: IgcBatchPresenter,
    wings: List<Wing>,
    actions: StepActions,
    onImport: () -> Unit,
) {
    if (presenter.loading) {
        Busy(null)
    } else {
        if (presenter.rows.isEmpty()) {
            Text(stringResource(Res.string.igc_batch_nothing_new))
        } else {
            WingForAll(presenter, wings)
            MatchSummary(presenter)
            LinkingHelp()
            TrackList(presenter, wings)
        }
        SkippedHint(presenter)
        if (presenter.failedCount > 0) {
            Text(
                stringResource(Res.string.igc_batch_result, presenter.importedCount, presenter.failedCount),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    Buttons {
        LeaveButtons(actions)
        Button(
            onClick = onImport,
            enabled = !presenter.loading && !presenter.importing && presenter.importableCount > 0,
        ) {
            val count = presenter.importableCount
            Text(pluralStringResource(Res.plurals.igc_batch_import, count, count))
        }
    }
}

/** What the import did with the tracks: attached to flights, logged as new ones, or left out. */
@Composable
internal fun IgcDoneStep(
    presenter: IgcBatchPresenter,
    onClose: () -> Unit,
) {
    Text(stringResource(Res.string.import_done_title), style = MaterialTheme.typography.titleMedium)
    val leftOut = presenter.rows.size
    Text(
        listOf(
            pluralStringResource(Res.plurals.igc_done_attached, presenter.attachedCount, presenter.attachedCount),
            pluralStringResource(Res.plurals.igc_done_created, presenter.createdCount, presenter.createdCount),
            pluralStringResource(Res.plurals.igc_done_left_out, leftOut, leftOut),
        ).joinToString(" · "),
    )
    SkippedHint(presenter)
    Buttons { Button(onClick = onClose) { Text(stringResource(Res.string.action_close)) } }
}

@Composable
private fun SkippedHint(presenter: IgcBatchPresenter) {
    if (presenter.skippedCount > 0) {
        Hint(pluralStringResource(Res.plurals.igc_batch_skipped, presenter.skippedCount, presenter.skippedCount))
    }
}

@Composable
private fun WingForAll(
    presenter: IgcBatchPresenter,
    wings: List<Wing>,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.igc_batch_wing_for_all), style = MaterialTheme.typography.bodyMedium)
        WingMenu(wings, presenter.defaultWingId, onSelected = { presenter.changeDefaultWing(it.id) })
    }
}

@Composable
private fun TrackList(
    presenter: IgcBatchPresenter,
    wings: List<Wing>,
) {
    LazyColumn(modifier = Modifier.heightIn(max = ROWS_MAX_HEIGHT_DP.dp)) {
        items(presenter.visibleRows) { row ->
            TrackRow(row, presenter, wings)
            HorizontalDivider()
        }
    }
}

@Composable
private fun TrackRow(
    row: IgcBatchRow,
    presenter: IgcBatchPresenter,
    wings: List<Wing>,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = row.selected,
            onCheckedChange = { presenter.select(row, it) },
            enabled = row.selectable && !presenter.importing,
        )
        TrackDetails(row, presenter, wings, Modifier.weight(1f))
        // An attached track goes to a flight that already has its wing.
        if (row.createsFlight) {
            WingMenu(wings, presenter.wingOf(row), onSelected = { presenter.changeWing(row, it.id) })
        }
    }
}

/** What the track says, which flight it belongs to, and what still needs the pilot's attention. */
@Composable
private fun TrackDetails(
    row: IgcBatchRow,
    presenter: IgcBatchPresenter,
    wings: List<Wing>,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val draft = row.draft
        val duration = draft?.duration
        if (draft != null && duration != null) {
            Text(
                "${draft.date.toDisplay()} · ${formatDuration(duration)}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            row.file.fileName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val takeOff = draft?.takeOffTime
        val landing = draft?.landingTime
        if (takeOff != null && landing != null) {
            Text(
                stringResource(Res.string.igc_detected, takeOff.toDisplay(), landing.toDisplay()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.match?.let { MatchLine(it, row) }
        if (row.selectable && row.canAttach) AttachChoice(row, presenter)
        row.problem?.let { StatusBadge(it.text(row.detail), it.severity()) }
        val date = draft?.date
        if (row.createsFlight && date != null &&
            wings.firstOrNull { it.id == presenter.wingOf(row) }?.wasInServiceOn(date) == false
        ) {
            StatusBadge(stringResource(Res.string.flight_warning_before_service), Severity.Warning)
        }
    }
}

/** A compact wing chooser: the current wing is the button, the others are its menu. */
@Composable
private fun WingMenu(
    wings: List<Wing>,
    selected: WingId,
    onSelected: (Wing) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = wings.firstOrNull { it.id == selected }
    TextButton(onClick = { expanded = true }) {
        Text(current?.let { wingLabel(it) }.orEmpty())
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

@Composable
private fun IgcRowProblem.text(detail: String?): String =
    when (this) {
        IgcRowProblem.Unreadable -> stringResource(Res.string.igc_problem_unreadable, detail.orEmpty())
        IgcRowProblem.NoFlightFound -> stringResource(Res.string.igc_problem_no_flight)
        IgcRowProblem.AlreadyImported -> stringResource(Res.string.igc_problem_duplicate)
        IgcRowProblem.RepeatedInSelection -> stringResource(Res.string.igc_problem_repeated)
        IgcRowProblem.DateInFuture -> stringResource(Res.string.igc_problem_future)
        IgcRowProblem.SaveFailed -> stringResource(Res.string.igc_problem_save, detail.orEmpty())
        IgcRowProblem.NoMatchingFlight -> stringResource(Res.string.igc_problem_no_match)
        IgcRowProblem.FlightHasTrack -> stringResource(Res.string.igc_problem_has_track)
    }

// Not yet imported but importable (a duplicate) is a warning; the rest cannot or did not work.
private fun IgcRowProblem.severity(): Severity =
    when (this) {
        // Only a hint: such a track can still be ticked.
        IgcRowProblem.NoMatchingFlight -> {
            Severity.Neutral
        }

        IgcRowProblem.AlreadyImported, IgcRowProblem.RepeatedInSelection, IgcRowProblem.FlightHasTrack -> {
            Severity.Warning
        }

        else -> {
            Severity.Critical
        }
    }
