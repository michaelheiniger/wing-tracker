package ch.qscqlmpa.wingtracker.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import ch.qscqlmpa.wingtracker.backup.CloudProvider
import ch.qscqlmpa.wingtracker.backup.CloudStorageSettings
import ch.qscqlmpa.wingtracker.ui.components.AppAlertDialog
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.platform.SecretKeeping
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.cloud_connected
import ch.qscqlmpa.wingtracker.ui.resources.cloud_edit
import ch.qscqlmpa.wingtracker.ui.resources.cloud_keep_all
import ch.qscqlmpa.wingtracker.ui.resources.cloud_keep_last
import ch.qscqlmpa.wingtracker.ui.resources.cloud_not_set_up
import ch.qscqlmpa.wingtracker.ui.resources.cloud_provider_mega
import ch.qscqlmpa.wingtracker.ui.resources.cloud_provider_other
import ch.qscqlmpa.wingtracker.ui.resources.cloud_region_hint_mega
import ch.qscqlmpa.wingtracker.ui.resources.cloud_remove
import ch.qscqlmpa.wingtracker.ui.resources.cloud_removed
import ch.qscqlmpa.wingtracker.ui.resources.cloud_saved
import ch.qscqlmpa.wingtracker.ui.resources.cloud_saved_session
import ch.qscqlmpa.wingtracker.ui.resources.cloud_saved_system
import ch.qscqlmpa.wingtracker.ui.resources.cloud_secret_kept_hint
import ch.qscqlmpa.wingtracker.ui.resources.cloud_set_up
import ch.qscqlmpa.wingtracker.ui.resources.cloud_summary
import ch.qscqlmpa.wingtracker.ui.resources.cloud_summary_bucket
import ch.qscqlmpa.wingtracker.ui.resources.cloud_test
import ch.qscqlmpa.wingtracker.ui.resources.cloud_testing
import ch.qscqlmpa.wingtracker.ui.resources.cloud_title
import ch.qscqlmpa.wingtracker.ui.resources.field_cloud_access_key
import ch.qscqlmpa.wingtracker.ui.resources.field_cloud_bucket
import ch.qscqlmpa.wingtracker.ui.resources.field_cloud_endpoint
import ch.qscqlmpa.wingtracker.ui.resources.field_cloud_keep_last
import ch.qscqlmpa.wingtracker.ui.resources.field_cloud_region
import ch.qscqlmpa.wingtracker.ui.resources.field_cloud_secret_key
import ch.qscqlmpa.wingtracker.ui.resources.settings_save
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A summary of the bucket backups go to; the form opens in a dialog, since it is filled in once (ADR 0032). */
@Composable
fun CloudStorageSection(presenter: CloudStoragePresenter) {
    val stored = presenter.stored
    if (stored == null) {
        Text(stringResource(Res.string.cloud_not_set_up), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = presenter::edit) { Text(stringResource(Res.string.cloud_set_up)) }
    } else {
        Summary(stored)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = presenter::edit, enabled = !presenter.busy) {
                Text(stringResource(Res.string.cloud_edit))
            }
            TestButton(presenter)
            TextButton(onClick = presenter::remove, enabled = !presenter.busy) {
                Text(stringResource(Res.string.cloud_remove))
            }
            Testing(presenter)
        }
    }
    // While the dialog is open, it shows them itself.
    if (!presenter.editing) Outcome(presenter)
    if (presenter.editing) CloudStorageDialog(presenter)
}

@Composable
private fun Summary(stored: CloudStorageSettings) {
    val place = if (stored.provider == CloudProvider.Other) stored.endpoint.orEmpty() else stored.region
    val keep =
        stored.keepLast?.let { pluralStringResource(Res.plurals.cloud_keep_last, it, it) }
            ?: stringResource(Res.string.cloud_keep_all)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(Res.string.cloud_summary, stringResource(stored.provider.label()), place),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            stringResource(Res.string.cloud_summary_bucket, stored.bucket, keep),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CloudStorageDialog(presenter: CloudStoragePresenter) {
    // Not closed by a click beside it, which would throw away the keys typed so far.
    AppAlertDialog(
        onDismissRequest = presenter::cancelEdit,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(Res.string.cloud_title)) },
        text = { Form(presenter) },
        confirmButton = {
            Button(onClick = presenter::save, enabled = !presenter.busy) {
                Text(stringResource(Res.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = presenter::cancelEdit) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun Form(presenter: CloudStoragePresenter) {
    val input = presenter.input
    // Scrolls, so the buttons below stay reachable in a low window.
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudProvider.entries.forEach { provider ->
                FilterChip(
                    selected = input.provider == provider,
                    onClick = { presenter.change { it.copy(provider = provider) } },
                    label = { Text(stringResource(provider.label())) },
                )
            }
        }
        if (input.provider == CloudProvider.Other) {
            Field(
                presenter,
                CloudStorageField.Endpoint,
                Res.string.field_cloud_endpoint,
                input.endpoint,
                Modifier.fillMaxWidth(),
            ) { copy(endpoint = it) }
        }
        Field(
            presenter,
            CloudStorageField.Region,
            Res.string.field_cloud_region,
            input.region,
            Modifier.fillMaxWidth(),
            hint = Res.string.cloud_region_hint_mega.takeIf { input.provider == CloudProvider.MegaS4 },
        ) { copy(region = it) }
        Field(
            presenter,
            CloudStorageField.Bucket,
            Res.string.field_cloud_bucket,
            input.bucket,
            Modifier.fillMaxWidth(),
        ) { copy(bucket = it) }
        Field(
            presenter,
            CloudStorageField.AccessKeyId,
            Res.string.field_cloud_access_key,
            input.accessKeyId,
            Modifier.fillMaxWidth(),
        ) { copy(accessKeyId = it) }
        Field(
            presenter,
            CloudStorageField.SecretKey,
            Res.string.field_cloud_secret_key,
            input.secretKey,
            Modifier.fillMaxWidth(),
            hint = Res.string.cloud_secret_kept_hint.takeIf { presenter.secretStored },
            secret = true,
        ) { copy(secretKey = it) }
        Field(
            presenter,
            CloudStorageField.KeepLast,
            Res.string.field_cloud_keep_last,
            input.keepLast,
            Modifier.fillMaxWidth(),
        ) { copy(keepLast = it) }
        // Here, and not among the dialog's buttons, since it checks what is typed without closing the form.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TestButton(presenter)
            Testing(presenter)
        }
        Outcome(presenter)
    }
}

@Composable
private fun TestButton(presenter: CloudStoragePresenter) {
    OutlinedButton(onClick = presenter::test, enabled = !presenter.busy) { Text(stringResource(Res.string.cloud_test)) }
}

@Composable
private fun Testing(presenter: CloudStoragePresenter) {
    if (presenter.busy) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(stringResource(Res.string.cloud_testing))
    }
}

@Composable
private fun Outcome(presenter: CloudStoragePresenter) {
    presenter.outcome?.let { Text(stringResource(it.text()), color = MaterialTheme.colorScheme.primary) }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun Field(
    presenter: CloudStoragePresenter,
    field: CloudStorageField,
    label: StringResource,
    value: String,
    modifier: Modifier,
    hint: StringResource? = null,
    secret: Boolean = false,
    update: CloudStorageInput.(String) -> CloudStorageInput,
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
        modifier = modifier,
    )
}

private fun CloudProvider.label() =
    when (this) {
        CloudProvider.MegaS4 -> Res.string.cloud_provider_mega
        CloudProvider.Other -> Res.string.cloud_provider_other
    }

private fun CloudStorageOutcome.text(): StringResource =
    when (this) {
        is CloudStorageOutcome.Saved -> {
            when (keeping) {
                SecretKeeping.System -> Res.string.cloud_saved_system
                SecretKeeping.Session -> Res.string.cloud_saved_session
                null -> Res.string.cloud_saved
            }
        }

        CloudStorageOutcome.Connected -> {
            Res.string.cloud_connected
        }

        CloudStorageOutcome.Removed -> {
            Res.string.cloud_removed
        }
    }
