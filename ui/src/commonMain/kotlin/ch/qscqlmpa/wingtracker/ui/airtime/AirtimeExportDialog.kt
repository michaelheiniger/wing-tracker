package ch.qscqlmpa.wingtracker.ui.airtime

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeExportCandidate
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeSkipReason
import ch.qscqlmpa.wingtracker.flights.airtime.GetAirtimePages
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.ui.components.AppDialog
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.flights.importing.Busy
import ch.qscqlmpa.wingtracker.ui.flights.importing.Buttons
import ch.qscqlmpa.wingtracker.ui.flights.importing.Hint
import ch.qscqlmpa.wingtracker.ui.flights.importing.IMPORT_DIALOG_WIDTH_DP
import ch.qscqlmpa.wingtracker.ui.flights.importing.IMPORT_LIST_MAX_HEIGHT_DP
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.isWorthRetrying
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.action_close
import ch.qscqlmpa.wingtracker.ui.resources.action_try_again
import ch.qscqlmpa.wingtracker.ui.resources.airtime_account_hint
import ch.qscqlmpa.wingtracker.ui.resources.airtime_already
import ch.qscqlmpa.wingtracker.ui.resources.airtime_choose_all
import ch.qscqlmpa.wingtracker.ui.resources.airtime_colour
import ch.qscqlmpa.wingtracker.ui.resources.airtime_gliders_created
import ch.qscqlmpa.wingtracker.ui.resources.airtime_gliders_hint
import ch.qscqlmpa.wingtracker.ui.resources.airtime_gliders_title
import ch.qscqlmpa.wingtracker.ui.resources.airtime_kind_manual
import ch.qscqlmpa.wingtracker.ui.resources.airtime_kind_track
import ch.qscqlmpa.wingtracker.ui.resources.airtime_nick
import ch.qscqlmpa.wingtracker.ui.resources.airtime_nothing
import ch.qscqlmpa.wingtracker.ui.resources.airtime_open_pilot
import ch.qscqlmpa.wingtracker.ui.resources.airtime_open_settings
import ch.qscqlmpa.wingtracker.ui.resources.airtime_read
import ch.qscqlmpa.wingtracker.ui.resources.airtime_reading
import ch.qscqlmpa.wingtracker.ui.resources.airtime_review_hint
import ch.qscqlmpa.wingtracker.ui.resources.airtime_send
import ch.qscqlmpa.wingtracker.ui.resources.airtime_sending
import ch.qscqlmpa.wingtracker.ui.resources.airtime_sent
import ch.qscqlmpa.wingtracker.ui.resources.airtime_skipped_no_time
import ch.qscqlmpa.wingtracker.ui.resources.airtime_skipped_no_wing
import ch.qscqlmpa.wingtracker.ui.resources.airtime_skipped_on_airtime
import ch.qscqlmpa.wingtracker.ui.resources.airtime_stop
import ch.qscqlmpa.wingtracker.ui.resources.airtime_stopped
import ch.qscqlmpa.wingtracker.ui.resources.airtime_title
import ch.qscqlmpa.wingtracker.ui.resources.airtime_token
import ch.qscqlmpa.wingtracker.ui.resources.airtime_token_hint
import ch.qscqlmpa.wingtracker.ui.resources.airtime_token_kept
import ch.qscqlmpa.wingtracker.ui.resources.airtime_token_replace
import ch.qscqlmpa.wingtracker.ui.resources.airtime_token_session
import ch.qscqlmpa.wingtracker.ui.resources.import_back
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Sends flights to air-time after a preview; nothing leaves the computer before the pilot confirms (ADR 0029). */
@Composable
fun AirtimeExportDialog(
    presenter: AirtimeExportPresenter,
    pages: GetAirtimePages,
    onClose: () -> Unit,
) {
    // A click beside the window would otherwise throw away a selection the pilot spent time on.
    AppDialog(onDismissRequest = onClose, properties = DialogProperties(dismissOnClickOutside = false)) {
        Card(modifier = Modifier.width(IMPORT_DIALOG_WIDTH_DP.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.airtime_title), style = MaterialTheme.typography.headlineSmall)
                when (presenter.stage) {
                    AirtimeStage.Account -> AccountStep(presenter, pages, onClose)
                    AirtimeStage.Reading -> ReadingStep(presenter)
                    AirtimeStage.Review -> ReviewStep(presenter, onClose)
                    AirtimeStage.Sending -> SendingStep(presenter)
                    AirtimeStage.Done -> DoneStep(presenter, pages, onClose)
                }
            }
        }
    }
}

@Composable
private fun AccountStep(
    presenter: AirtimeExportPresenter,
    pages: GetAirtimePages,
    onClose: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Hint(stringResource(Res.string.airtime_account_hint))
    OutlinedTextField(
        value = presenter.nick,
        onValueChange = presenter::changeNick,
        label = { Text(stringResource(Res.string.airtime_nick)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (presenter.hasToken) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusBadge(stringResource(Res.string.airtime_token_kept), Severity.Success)
            TextButton(onClick = presenter::forgetToken) { Text(stringResource(Res.string.airtime_token_replace)) }
        }
    } else {
        Hint(stringResource(Res.string.airtime_token_hint))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = presenter.tokenInput,
                onValueChange = presenter::changeToken,
                label = { Text(stringResource(Res.string.airtime_token)) },
                singleLine = true,
                // Shown as dots: it acts as the pilot, and screens get shared.
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { uriHandler.openUri(pages.tokenPage()) }) {
                Text(stringResource(Res.string.airtime_open_settings))
            }
        }
    }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    Buttons {
        TextButton(onClick = onClose) { Text(stringResource(Res.string.action_cancel)) }
        Button(onClick = presenter::read, enabled = presenter.canRead) {
            val retry = presenter.failure?.isWorthRetrying == true
            Text(stringResource(if (retry) Res.string.action_try_again else Res.string.airtime_read))
        }
    }
}

@Composable
private fun ReadingStep(presenter: AirtimeExportPresenter) {
    Busy(stringResource(Res.string.airtime_reading))
    Buttons { TextButton(onClick = presenter::cancelReading) { Text(stringResource(Res.string.action_cancel)) } }
}

@Composable
private fun ReviewStep(
    presenter: AirtimeExportPresenter,
    onClose: () -> Unit,
) {
    val draft = checkNotNull(presenter.draft)
    if (presenter.keeping == TokenKeeping.Session) {
        StatusBadge(stringResource(Res.string.airtime_token_session), Severity.Warning)
    }
    if (draft.alreadyExported > 0) Hint(plural(Res.plurals.airtime_already, draft.alreadyExported))
    Skipped(draft.skipped.groupingBy { it.reason }.eachCount())
    if (draft.candidates.isEmpty()) {
        Text(stringResource(Res.string.airtime_nothing))
    } else {
        Hint(stringResource(Res.string.airtime_review_hint))
        FlightList(presenter, draft.candidates)
        NewGliders(presenter)
    }
    Buttons {
        TextButton(onClick = presenter::back) { Text(stringResource(Res.string.import_back)) }
        TextButton(onClick = onClose) { Text(stringResource(Res.string.action_cancel)) }
        if (draft.candidates.isNotEmpty()) {
            Button(onClick = presenter::send, enabled = presenter.canSend) {
                Text(plural(Res.plurals.airtime_send, presenter.selection.chosen.size))
            }
        }
    }
}

@Composable
private fun Skipped(counts: Map<AirtimeSkipReason, Int>) {
    counts[AirtimeSkipReason.AlreadyOnAirtime]?.let { Hint(plural(Res.plurals.airtime_skipped_on_airtime, it)) }
    counts[AirtimeSkipReason.WingNotFound]?.let { Hint(plural(Res.plurals.airtime_skipped_no_wing, it)) }
    // The only reason the pilot can act on, so it stands out.
    counts[AirtimeSkipReason.NoTakeOffTime]?.let {
        StatusBadge(plural(Res.plurals.airtime_skipped_no_time, it), Severity.Warning)
    }
}

@Composable
private fun FlightList(
    presenter: AirtimeExportPresenter,
    candidates: List<AirtimeExportCandidate>,
) {
    val state =
        when (presenter.selection.chosen.size) {
            0 -> ToggleableState.Off
            candidates.size -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TriStateCheckbox(state = state, onClick = { presenter.selection.chooseAll(state != ToggleableState.On) })
        Text(stringResource(Res.string.airtime_choose_all), style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider()
    // Newest first, like the logbook.
    val shown = candidates.asReversed()
    LazyColumn(modifier = Modifier.heightIn(max = IMPORT_LIST_MAX_HEIGHT_DP.dp)) {
        items(shown, key = { it.flight.id.value }) { candidate ->
            FlightRow(candidate, candidate.flight.id in presenter.selection.chosen) {
                presenter.selection.toggle(candidate.flight.id)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun FlightRow(
    candidate: AirtimeExportCandidate,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val details = candidate.flight.details
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            val time = details.takeOffTime?.let { " ${it.toDisplay()}" }.orEmpty()
            Text(
                "${details.date.toDisplay()}$time · ${formatDuration(details.duration)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Hint(listOfNotNull(candidate.wing.details.name, details.takeOffSite).joinToString(" · "))
        }
        if (candidate.withTrack) {
            StatusBadge(stringResource(Res.string.airtime_kind_track), Severity.Neutral)
        } else {
            StatusBadge(stringResource(Res.string.airtime_kind_manual), Severity.Neutral)
        }
    }
}

/** The colour of each glider to create; air-time requires one, and wing-tracker does not keep it. */
@Composable
private fun NewGliders(presenter: AirtimeExportPresenter) {
    val needed = presenter.selection.gliderNeeded
    if (needed.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.airtime_gliders_title), style = MaterialTheme.typography.titleSmall)
            Hint(stringResource(Res.string.airtime_gliders_hint))
            needed.forEach { wing ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(wing.details.name)
                        Hint(
                            listOfNotNull(wing.details.manufacturer, wing.details.model, wing.details.size)
                                .joinToString(" "),
                        )
                    }
                    OutlinedTextField(
                        value = presenter.selection.colours[wing.id].orEmpty(),
                        onValueChange = { presenter.selection.changeColour(wing.id, it) },
                        label = { Text(stringResource(Res.string.airtime_colour)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SendingStep(presenter: AirtimeExportPresenter) {
    val progress = checkNotNull(presenter.progress)
    Text(stringResource(Res.string.airtime_sending, progress.done, progress.total))
    LinearProgressIndicator(
        progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
        modifier = Modifier.fillMaxWidth(),
    )
    Buttons {
        TextButton(onClick = presenter::stopSending) { Text(stringResource(Res.string.airtime_stop)) }
    }
}

@Composable
private fun DoneStep(
    presenter: AirtimeExportPresenter,
    pages: GetAirtimePages,
    onClose: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val result = presenter.result
    if (presenter.stopped) {
        StatusBadge(stringResource(Res.string.airtime_stopped), Severity.Warning)
    } else if (result != null) {
        val severity = if (result.failure == null) Severity.Success else Severity.Warning
        StatusBadge(plural(Res.plurals.airtime_sent, result.exported), severity)
        if (result.glidersCreated > 0) Hint(plural(Res.plurals.airtime_gliders_created, result.glidersCreated))
    }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    Buttons {
        OutlinedButton(onClick = { uriHandler.openUri(pages.pilotPage(presenter.nick)) }) {
            Text(stringResource(Res.string.airtime_open_pilot))
        }
        Button(onClick = onClose) { Text(stringResource(Res.string.action_close)) }
    }
}

@Composable
private fun plural(
    resource: PluralStringResource,
    count: Int,
) = pluralStringResource(resource, count, count)
