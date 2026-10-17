package ch.qscqlmpa.wingtracker.ui.syride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.flights.importing.Busy
import ch.qscqlmpa.wingtracker.ui.flights.importing.Buttons
import ch.qscqlmpa.wingtracker.ui.flights.importing.Hint
import ch.qscqlmpa.wingtracker.ui.flights.importing.ImportDone
import ch.qscqlmpa.wingtracker.ui.flights.importing.LeaveButtons
import ch.qscqlmpa.wingtracker.ui.flights.importing.StepActions
import ch.qscqlmpa.wingtracker.ui.flights.importing.WingGroupList
import ch.qscqlmpa.wingtracker.ui.isWorthRetrying
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.action_try_again
import ch.qscqlmpa.wingtracker.ui.resources.import_back
import ch.qscqlmpa.wingtracker.ui.resources.import_count
import ch.qscqlmpa.wingtracker.ui.resources.import_nothing_new
import ch.qscqlmpa.wingtracker.ui.resources.syride_account_hint
import ch.qscqlmpa.wingtracker.ui.resources.syride_already
import ch.qscqlmpa.wingtracker.ui.resources.syride_read
import ch.qscqlmpa.wingtracker.ui.resources.syride_reading
import ch.qscqlmpa.wingtracker.ui.resources.syride_time_example
import ch.qscqlmpa.wingtracker.ui.resources.syride_time_none
import ch.qscqlmpa.wingtracker.ui.resources.syride_times_found
import ch.qscqlmpa.wingtracker.ui.resources.syride_times_more
import ch.qscqlmpa.wingtracker.ui.resources.syride_times_updated
import ch.qscqlmpa.wingtracker.ui.resources.syride_times_why
import ch.qscqlmpa.wingtracker.ui.resources.syride_update_times
import ch.qscqlmpa.wingtracker.ui.resources.syride_username
import ch.qscqlmpa.wingtracker.ui.resources.syride_wings_hint
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The steps of the import from Syride, shown inside the import wizard. */
@Composable
internal fun SyrideImportSteps(
    presenter: SyrideImportPresenter,
    actions: StepActions,
) {
    when (presenter.stage) {
        SyrideStage.Account -> AccountStep(presenter, actions)
        SyrideStage.Reading -> ReadingStep(presenter)
        SyrideStage.Wings -> WingsStep(presenter, actions)
        SyrideStage.Importing -> Busy(null)
        SyrideStage.Done -> ImportDone(presenter.result, actions.close)
    }
}

@Composable
private fun AccountStep(
    presenter: SyrideImportPresenter,
    actions: StepActions,
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
        LeaveButtons(actions)
        Button(onClick = presenter::read, enabled = presenter.canRead) {
            Text(
                stringResource(
                    if (presenter.failure?.isWorthRetrying ==
                        true
                    ) {
                        Res.string.action_try_again
                    } else {
                        Res.string.syride_read
                    },
                ),
            )
        }
    }
}

@Composable
private fun ReadingStep(presenter: SyrideImportPresenter) {
    Busy(stringResource(Res.string.syride_reading))
    Buttons { TextButton(onClick = presenter::cancelReading) { Text(stringResource(Res.string.action_cancel)) } }
}

@Composable
private fun WingsStep(
    presenter: SyrideImportPresenter,
    actions: StepActions,
) {
    val draft = checkNotNull(presenter.draft)
    if (draft.alreadyImported > 0) Hint(stringResource(Res.string.syride_already, draft.alreadyImported))
    TakeOffTimes(presenter)
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
        TextButton(onClick = actions.cancel) { Text(stringResource(Res.string.action_cancel)) }
        Button(
            onClick = { presenter.import(onFinished = {}) },
            enabled = presenter.importableCount > 0 && presenter.allReady,
        ) {
            Text(pluralStringResource(Res.plurals.import_count, presenter.importableCount, presenter.importableCount))
        }
    }
}

private const val TIME_EXAMPLES = 3

/** Flights imported earlier that can get Syride's take-off time, with a few examples so the pilot sees the change. */
@Composable
private fun TakeOffTimes(presenter: SyrideImportPresenter) {
    presenter.updatedTimes?.let {
        StatusBadge(pluralStringResource(Res.plurals.syride_times_updated, it, it), Severity.Success)
        return
    }
    val updates = checkNotNull(presenter.draft).takeOffTimeUpdates
    if (updates.isEmpty()) return
    val none = stringResource(Res.string.syride_time_none)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(pluralStringResource(Res.plurals.syride_times_found, updates.size, updates.size))
            updates.sortedByDescending { it.date }.take(TIME_EXAMPLES).forEach {
                val stored = it.stored?.toDisplay() ?: none
                Hint(
                    stringResource(
                        Res.string.syride_time_example,
                        it.date.toDisplay(),
                        stored,
                        it.fromSyride.toDisplay(),
                    ),
                )
            }
            val more = updates.size - TIME_EXAMPLES
            if (more > 0) Hint(pluralStringResource(Res.plurals.syride_times_more, more, more))
            Hint(stringResource(Res.string.syride_times_why))
            Buttons {
                Button(onClick = presenter::updateTakeOffTimes, enabled = !presenter.updatingTimes) {
                    Text(pluralStringResource(Res.plurals.syride_update_times, updates.size, updates.size))
                }
            }
        }
    }
}
