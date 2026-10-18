package ch.qscqlmpa.wingtracker.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import ch.qscqlmpa.wingtracker.backup.CloudProvider
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.platform.SecretKeeping
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.cloud_connected
import ch.qscqlmpa.wingtracker.ui.resources.cloud_provider_mega
import ch.qscqlmpa.wingtracker.ui.resources.cloud_provider_other
import ch.qscqlmpa.wingtracker.ui.resources.cloud_region_hint_mega
import ch.qscqlmpa.wingtracker.ui.resources.cloud_remove
import ch.qscqlmpa.wingtracker.ui.resources.cloud_removed
import ch.qscqlmpa.wingtracker.ui.resources.cloud_saved
import ch.qscqlmpa.wingtracker.ui.resources.cloud_saved_session
import ch.qscqlmpa.wingtracker.ui.resources.cloud_saved_system
import ch.qscqlmpa.wingtracker.ui.resources.cloud_secret_kept_hint
import ch.qscqlmpa.wingtracker.ui.resources.cloud_test
import ch.qscqlmpa.wingtracker.ui.resources.cloud_testing
import ch.qscqlmpa.wingtracker.ui.resources.field_cloud_access_key
import ch.qscqlmpa.wingtracker.ui.resources.field_cloud_bucket
import ch.qscqlmpa.wingtracker.ui.resources.field_cloud_endpoint
import ch.qscqlmpa.wingtracker.ui.resources.field_cloud_keep_last
import ch.qscqlmpa.wingtracker.ui.resources.field_cloud_region
import ch.qscqlmpa.wingtracker.ui.resources.field_cloud_secret_key
import ch.qscqlmpa.wingtracker.ui.resources.settings_save
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun CloudStorageSection(presenter: CloudStoragePresenter) {
    val input = presenter.input
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CloudProvider.entries.forEach { provider ->
            FilterChip(
                selected = input.provider == provider,
                onClick = { presenter.change { it.copy(provider = provider) } },
                label = { Text(stringResource(provider.label())) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (input.provider == CloudProvider.Other) {
            Field(
                presenter,
                CloudStorageField.Endpoint,
                Res.string.field_cloud_endpoint,
                input.endpoint,
                Modifier.weight(2f),
            ) {
                copy(endpoint = it)
            }
        }
        Field(
            presenter,
            CloudStorageField.Region,
            Res.string.field_cloud_region,
            input.region,
            Modifier.weight(1f),
            hint = Res.string.cloud_region_hint_mega.takeIf { input.provider == CloudProvider.MegaS4 },
        ) { copy(region = it) }
        Field(presenter, CloudStorageField.Bucket, Res.string.field_cloud_bucket, input.bucket, Modifier.weight(1f)) {
            copy(bucket = it)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Field(
            presenter,
            CloudStorageField.AccessKeyId,
            Res.string.field_cloud_access_key,
            input.accessKeyId,
            Modifier.weight(1f),
        ) {
            copy(accessKeyId = it)
        }
        Field(
            presenter,
            CloudStorageField.SecretKey,
            Res.string.field_cloud_secret_key,
            input.secretKey,
            Modifier.weight(1f),
            hint = Res.string.cloud_secret_kept_hint.takeIf { presenter.secretStored },
            secret = true,
        ) { copy(secretKey = it) }
        Field(
            presenter,
            CloudStorageField.KeepLast,
            Res.string.field_cloud_keep_last,
            input.keepLast,
            Modifier.weight(1f),
        ) {
            copy(keepLast = it)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = presenter::save, enabled = !presenter.busy) { Text(stringResource(Res.string.settings_save)) }
        OutlinedButton(
            onClick = presenter::test,
            enabled = !presenter.busy,
        ) { Text(stringResource(Res.string.cloud_test)) }
        if (presenter.configured) {
            TextButton(onClick = presenter::remove, enabled = !presenter.busy) {
                Text(stringResource(Res.string.cloud_remove))
            }
        }
        if (presenter.busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(Res.string.cloud_testing))
        }
    }
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
