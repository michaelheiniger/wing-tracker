package ch.qscqlmpa.wingtracker.ui.syride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ch.qscqlmpa.wingtracker.ui.flights.importing.Busy
import ch.qscqlmpa.wingtracker.ui.flights.importing.Buttons
import ch.qscqlmpa.wingtracker.ui.flights.importing.Hint
import ch.qscqlmpa.wingtracker.ui.flights.importing.IMPORT_DIALOG_WIDTH_DP
import ch.qscqlmpa.wingtracker.ui.flights.importing.ImportDone
import ch.qscqlmpa.wingtracker.ui.flights.importing.WingGroupList
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.import_back
import ch.qscqlmpa.wingtracker.ui.resources.import_count
import ch.qscqlmpa.wingtracker.ui.resources.import_nothing_new
import ch.qscqlmpa.wingtracker.ui.resources.syride_account_hint
import ch.qscqlmpa.wingtracker.ui.resources.syride_already
import ch.qscqlmpa.wingtracker.ui.resources.syride_read
import ch.qscqlmpa.wingtracker.ui.resources.syride_reading
import ch.qscqlmpa.wingtracker.ui.resources.syride_title
import ch.qscqlmpa.wingtracker.ui.resources.syride_username
import ch.qscqlmpa.wingtracker.ui.resources.syride_wings_hint
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The import from Syride, step by step. */
@Composable
fun SyrideImportDialog(
    presenter: SyrideImportPresenter,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = { if (presenter.stage != SyrideStage.Importing) onClose() }) {
        Card(modifier = Modifier.width(IMPORT_DIALOG_WIDTH_DP.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.syride_title), style = MaterialTheme.typography.headlineSmall)
                when (presenter.stage) {
                    SyrideStage.Account -> AccountStep(presenter, onClose)
                    SyrideStage.Reading -> Busy(stringResource(Res.string.syride_reading))
                    SyrideStage.Wings -> WingsStep(presenter, onClose)
                    SyrideStage.Importing -> Busy(null)
                    SyrideStage.Done -> ImportDone(presenter.result, onClose)
                }
            }
        }
    }
}

@Composable
private fun AccountStep(
    presenter: SyrideImportPresenter,
    onClose: () -> Unit,
) {
    Hint(stringResource(Res.string.syride_account_hint))
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
    if (draft.alreadyImported > 0) Hint(stringResource(Res.string.syride_already, draft.alreadyImported))
    if (draft.groups.isEmpty()) {
        Text(stringResource(Res.string.import_nothing_new))
    } else {
        WingGroupList(
            draft.groups,
            presenter.mapping,
            draft.existingWings,
            hint = stringResource(Res.string.syride_wings_hint),
        )
    }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    Buttons {
        TextButton(onClick = presenter::back) { Text(stringResource(Res.string.import_back)) }
        TextButton(onClick = onClose) { Text(stringResource(Res.string.action_cancel)) }
        Button(
            onClick = { presenter.import(onFinished = {}) },
            enabled = presenter.importableCount > 0 && presenter.allReady,
        ) {
            Text(pluralStringResource(Res.plurals.import_count, presenter.importableCount, presenter.importableCount))
        }
    }
}
