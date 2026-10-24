package ch.qscqlmpa.wingtracker.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ch.qscqlmpa.wingtracker.sync.RecordKind
import ch.qscqlmpa.wingtracker.sync.SyncStatus
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import ch.qscqlmpa.wingtracker.ui.components.AppAlertDialog
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.platform.SecretKeeping
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.cloud_secret_kept_hint
import ch.qscqlmpa.wingtracker.ui.resources.field_sync_author_email
import ch.qscqlmpa.wingtracker.ui.resources.field_sync_author_name
import ch.qscqlmpa.wingtracker.ui.resources.field_sync_repository
import ch.qscqlmpa.wingtracker.ui.resources.field_sync_token
import ch.qscqlmpa.wingtracker.ui.resources.field_sync_username
import ch.qscqlmpa.wingtracker.ui.resources.flight_date_time
import ch.qscqlmpa.wingtracker.ui.resources.settings_save
import ch.qscqlmpa.wingtracker.ui.resources.sync_author_hint
import ch.qscqlmpa.wingtracker.ui.resources.sync_conflicts
import ch.qscqlmpa.wingtracker.ui.resources.sync_connect
import ch.qscqlmpa.wingtracker.ui.resources.sync_connect_open
import ch.qscqlmpa.wingtracker.ui.resources.sync_connect_title
import ch.qscqlmpa.wingtracker.ui.resources.sync_connected_session
import ch.qscqlmpa.wingtracker.ui.resources.sync_connected_system
import ch.qscqlmpa.wingtracker.ui.resources.sync_connecting
import ch.qscqlmpa.wingtracker.ui.resources.sync_edit
import ch.qscqlmpa.wingtracker.ui.resources.sync_edit_title
import ch.qscqlmpa.wingtracker.ui.resources.sync_in_progress
import ch.qscqlmpa.wingtracker.ui.resources.sync_last
import ch.qscqlmpa.wingtracker.ui.resources.sync_not_connected
import ch.qscqlmpa.wingtracker.ui.resources.sync_now
import ch.qscqlmpa.wingtracker.ui.resources.sync_received
import ch.qscqlmpa.wingtracker.ui.resources.sync_record_attachment
import ch.qscqlmpa.wingtracker.ui.resources.sync_record_flight
import ch.qscqlmpa.wingtracker.ui.resources.sync_record_harness
import ch.qscqlmpa.wingtracker.ui.resources.sync_record_other
import ch.qscqlmpa.wingtracker.ui.resources.sync_record_reserve
import ch.qscqlmpa.wingtracker.ui.resources.sync_record_settings
import ch.qscqlmpa.wingtracker.ui.resources.sync_record_wing
import ch.qscqlmpa.wingtracker.ui.resources.sync_repository_hint
import ch.qscqlmpa.wingtracker.ui.resources.sync_saved
import ch.qscqlmpa.wingtracker.ui.resources.sync_saved_session
import ch.qscqlmpa.wingtracker.ui.resources.sync_saved_system
import ch.qscqlmpa.wingtracker.ui.resources.sync_sent
import ch.qscqlmpa.wingtracker.ui.resources.sync_summary_done
import ch.qscqlmpa.wingtracker.ui.resources.sync_token_hint
import ch.qscqlmpa.wingtracker.ui.resources.sync_up_to_date
import ch.qscqlmpa.wingtracker.ui.resources.sync_username_hint
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The repository the logbook is synchronised to, and a sync by hand; the form opens in a dialog (ADR 0033). */
@Composable
fun SyncSection(presenter: SyncPresenter) {
    val status = presenter.status ?: return
    val settings = status.settings
    if (settings == null) {
        Text(stringResource(Res.string.sync_not_connected), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = presenter::edit) { Text(stringResource(Res.string.sync_connect_open)) }
    } else {
        Summary(settings.repository, status)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = presenter::syncNow, enabled = !presenter.busy) {
                Text(stringResource(Res.string.sync_now))
            }
            OutlinedButton(onClick = presenter::edit, enabled = !presenter.busy) {
                Text(stringResource(Res.string.sync_edit))
            }
            Busy(presenter, Res.string.sync_in_progress)
        }
    }
    // While the dialog is open, it shows them itself.
    if (!presenter.editing) Outcome(presenter)
    if (presenter.editing) SyncDialog(presenter)
}

@Composable
private fun Summary(
    repository: String,
    status: SyncStatus,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(repository, style = MaterialTheme.typography.bodyLarge)
        status.lastSyncedAt?.let {
            Text(
                stringResource(
                    Res.string.sync_last,
                    stringResource(Res.string.flight_date_time, it.date.toDisplay(), it.time.toDisplay()),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncDialog(presenter: SyncPresenter) {
    // Not closed by a click beside it, which would throw away the token typed so far.
    AppAlertDialog(
        onDismissRequest = presenter::cancelEdit,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Text(stringResource(if (presenter.connected) Res.string.sync_edit_title else Res.string.sync_connect_title))
        },
        text = { Form(presenter) },
        confirmButton = {
            Button(onClick = presenter::save, enabled = !presenter.busy) {
                Text(stringResource(if (presenter.connected) Res.string.settings_save else Res.string.sync_connect))
            }
        },
        dismissButton = {
            // Also stops a connect still running.
            TextButton(onClick = presenter::cancelEdit) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun Form(presenter: SyncPresenter) {
    val input = presenter.input
    // Scrolls, so the buttons below stay reachable in a low window.
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        // The repository stays the one connected to; another one would hold another logbook.
        Field(
            presenter,
            SyncField.Repository,
            Res.string.field_sync_repository,
            input.repository,
            hint = Res.string.sync_repository_hint,
            enabled = !presenter.connected,
        ) { copy(repository = it) }
        Field(
            presenter,
            SyncField.Username,
            Res.string.field_sync_username,
            input.username,
            hint = Res.string.sync_username_hint,
        ) { copy(username = it) }
        Field(
            presenter,
            SyncField.Token,
            Res.string.field_sync_token,
            input.token,
            hint =
                if (presenter.connected && presenter.tokenStored) {
                    Res.string.cloud_secret_kept_hint
                } else {
                    Res.string.sync_token_hint
                },
            secret = true,
        ) { copy(token = it) }
        Field(
            presenter,
            SyncField.AuthorName,
            Res.string.field_sync_author_name,
            input.authorName,
            hint = Res.string.sync_author_hint,
        ) { copy(authorName = it) }
        Field(presenter, SyncField.AuthorEmail, Res.string.field_sync_author_email, input.authorEmail) {
            copy(authorEmail = it)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Busy(presenter, Res.string.sync_connecting)
        }
        Outcome(presenter)
    }
}

@Composable
private fun Busy(
    presenter: SyncPresenter,
    label: StringResource,
) {
    if (presenter.busy) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(stringResource(label))
        TextButton(onClick = presenter::cancel) { Text(stringResource(Res.string.action_cancel)) }
    }
}

@Composable
private fun Outcome(presenter: SyncPresenter) {
    presenter.outcome?.let { outcome ->
        // Conflicts are the one outcome that leaves something to do.
        val conflicts = outcome is SyncOutcome.Conflicts
        val color = if (conflicts) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        Text(outcome.text(), color = color)
    }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun SyncOutcome.text(): String =
    when (this) {
        is SyncOutcome.Connected -> {
            stringResource(
                when (keeping) {
                    SecretKeeping.System -> Res.string.sync_connected_system
                    SecretKeeping.Session -> Res.string.sync_connected_session
                },
            )
        }

        is SyncOutcome.Saved -> {
            stringResource(
                when (keeping) {
                    SecretKeeping.System -> Res.string.sync_saved_system
                    SecretKeeping.Session -> Res.string.sync_saved_session
                    null -> Res.string.sync_saved
                },
            )
        }

        is SyncOutcome.Synced -> {
            if (received == 0 && sent == 0) {
                stringResource(Res.string.sync_up_to_date)
            } else {
                stringResource(
                    Res.string.sync_summary_done,
                    pluralStringResource(Res.plurals.sync_received, received, received),
                    pluralStringResource(Res.plurals.sync_sent, sent, sent),
                )
            }
        }

        is SyncOutcome.Conflicts -> {
            stringResource(Res.string.sync_conflicts, records.described())
        }
    }

/** Counts per kind, e.g. "1 flight, 2 wings", since the records have no names here yet. */
@Composable
private fun List<SyncedRecord>.described(): String =
    groupingBy { it.kind }
        .eachCount()
        .entries
        .sortedBy { it.key.ordinal }
        .map { (kind, count) -> pluralStringResource(kind.plural(), count, count) }
        .joinToString(", ")

private fun RecordKind.plural(): PluralStringResource =
    when (this) {
        RecordKind.Wing -> Res.plurals.sync_record_wing
        RecordKind.Harness -> Res.plurals.sync_record_harness
        RecordKind.Reserve -> Res.plurals.sync_record_reserve
        RecordKind.Flight -> Res.plurals.sync_record_flight
        RecordKind.Attachment -> Res.plurals.sync_record_attachment
        RecordKind.LogbookSettings -> Res.plurals.sync_record_settings
        RecordKind.Other -> Res.plurals.sync_record_other
    }

@Composable
private fun Field(
    presenter: SyncPresenter,
    field: SyncField,
    label: StringResource,
    value: String,
    hint: StringResource? = null,
    secret: Boolean = false,
    enabled: Boolean = true,
    update: SyncInput.(String) -> SyncInput,
) {
    val error = presenter.errors[field]
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> presenter.change { it.update(typed) } },
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = {
            when {
                error != null -> Text(error.message())
                hint != null -> Text(stringResource(hint))
            }
        },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}
