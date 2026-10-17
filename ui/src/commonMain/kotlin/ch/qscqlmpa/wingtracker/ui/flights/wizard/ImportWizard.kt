package ch.qscqlmpa.wingtracker.ui.flights.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.ui.flights.IgcDoneStep
import ch.qscqlmpa.wingtracker.ui.flights.IgcReviewStep
import ch.qscqlmpa.wingtracker.ui.flights.importing.IMPORT_DIALOG_WIDTH_DP
import ch.qscqlmpa.wingtracker.ui.flights.importing.StepActions
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.igc_batch_title
import ch.qscqlmpa.wingtracker.ui.resources.import_cancel_body
import ch.qscqlmpa.wingtracker.ui.resources.import_cancel_confirm
import ch.qscqlmpa.wingtracker.ui.resources.import_cancel_keep
import ch.qscqlmpa.wingtracker.ui.resources.import_cancel_title
import ch.qscqlmpa.wingtracker.ui.resources.import_wizard_title
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_title
import ch.qscqlmpa.wingtracker.ui.resources.syride_title
import ch.qscqlmpa.wingtracker.ui.spreadsheet.SpreadsheetImportSteps
import ch.qscqlmpa.wingtracker.ui.syride.SyrideImportSteps
import ch.qscqlmpa.wingtracker.ui.syride.TrackOffer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Every way to bring flights in, one step after the other, in a single window. */
@Composable
fun ImportWizard(
    presenter: ImportWizardPresenter,
    wings: List<Wing>,
) {
    // A click beside the window would otherwise throw away a review the pilot spent time on.
    Dialog(onDismissRequest = presenter::cancel, properties = DialogProperties(dismissOnClickOutside = false)) {
        Card(modifier = Modifier.width(IMPORT_DIALOG_WIDTH_DP.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(presenter.flow?.source.title()), style = MaterialTheme.typography.headlineSmall)
                Stepper(presenter.steps, presenter.step)
                val actions =
                    StepActions(
                        back = if (presenter.canGoBack) presenter::back else null,
                        cancel = presenter::cancel,
                        close = presenter::close,
                    )
                when (val flow = presenter.flow) {
                    null -> {
                        SourceStep(presenter, hasWings = wings.isNotEmpty())
                    }

                    is ImportFlow.Syride -> {
                        SyrideImportSteps(
                            flow.presenter,
                            actions,
                            TrackOffer(
                                onImported = presenter::imported,
                                onChooseFiles = { presenter.chooseTracks(ImportSource.IgcFiles) },
                                onReadDevice = { presenter.chooseTracks(ImportSource.Device) },
                                failure = presenter.pickFailure,
                            ),
                        )
                    }

                    is ImportFlow.Spreadsheet -> {
                        SpreadsheetImportSteps(flow.presenter, actions, onChooseAgain = presenter::chooseSpreadsheet)
                    }

                    is ImportFlow.Tracks -> {
                        if (flow.done) {
                            IgcDoneStep(flow.presenter, presenter::close)
                        } else {
                            IgcReviewStep(flow.presenter, wings, actions, onImport = presenter::importTracks)
                        }
                    }
                }
            }
        }
    }
    if (presenter.confirmingCancel) CancelConfirmation(presenter)
}

private fun ImportSource?.title(): StringResource =
    when (this) {
        null -> Res.string.import_wizard_title
        ImportSource.Syride -> Res.string.syride_title
        ImportSource.IgcFiles, ImportSource.Device -> Res.string.igc_batch_title
        ImportSource.Spreadsheet -> Res.string.spreadsheet_title
    }

@Composable
private fun CancelConfirmation(presenter: ImportWizardPresenter) {
    AlertDialog(
        onDismissRequest = presenter::keepImporting,
        title = { Text(stringResource(Res.string.import_cancel_title)) },
        text = { Text(stringResource(Res.string.import_cancel_body)) },
        confirmButton = {
            Button(onClick = presenter::confirmCancel) { Text(stringResource(Res.string.import_cancel_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = presenter::keepImporting) { Text(stringResource(Res.string.import_cancel_keep)) }
        },
    )
}
