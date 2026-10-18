package ch.qscqlmpa.wingtracker.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.components.AppAlertDialog
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.backup_create
import ch.qscqlmpa.wingtracker.ui.resources.backup_hint
import ch.qscqlmpa.wingtracker.ui.resources.backup_in_progress
import ch.qscqlmpa.wingtracker.ui.resources.backup_last
import ch.qscqlmpa.wingtracker.ui.resources.backup_never
import ch.qscqlmpa.wingtracker.ui.resources.backup_restore
import ch.qscqlmpa.wingtracker.ui.resources.backup_saved
import ch.qscqlmpa.wingtracker.ui.resources.backup_saved_content
import ch.qscqlmpa.wingtracker.ui.resources.backup_title
import ch.qscqlmpa.wingtracker.ui.resources.flight_date_time
import ch.qscqlmpa.wingtracker.ui.resources.restore_choose_folder
import ch.qscqlmpa.wingtracker.ui.resources.restore_how
import ch.qscqlmpa.wingtracker.ui.resources.restore_in_progress
import ch.qscqlmpa.wingtracker.ui.resources.restore_made
import ch.qscqlmpa.wingtracker.ui.resources.restore_newer
import ch.qscqlmpa.wingtracker.ui.resources.restore_newer_badge
import ch.qscqlmpa.wingtracker.ui.resources.restore_restart_body
import ch.qscqlmpa.wingtracker.ui.resources.restore_title
import ch.qscqlmpa.wingtracker.ui.settings.RestartDialog
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val BYTES_PER_MB = 1024.0 * 1024.0

@Composable
fun BackupSection(
    presenter: BackupPresenter,
    restore: RestorePresenter,
) {
    Text(
        stringResource(Res.string.backup_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text(
        stringResource(Res.string.backup_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        presenter.lastMadeAt?.let {
            stringResource(
                Res.string.backup_last,
                stringResource(Res.string.flight_date_time, it.date.toDisplay(), it.time.toDisplay()),
            )
        } ?: stringResource(Res.string.backup_never),
        style = MaterialTheme.typography.bodyLarge,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = presenter::create, enabled = !presenter.busy) {
            Text(stringResource(Res.string.backup_create))
        }
        OutlinedButton(onClick = restore::start, enabled = !presenter.busy && restore.step == null) {
            Text(stringResource(Res.string.backup_restore))
        }
        if (presenter.busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(Res.string.backup_in_progress))
        }
    }
    presenter.saved?.let { saved ->
        // The path is selectable, so the pilot can find the file again or copy it elsewhere.
        Text(stringResource(Res.string.backup_saved, saved.path))
        Text(
            pluralStringResource(
                Res.plurals.backup_saved_content,
                saved.summary.files,
                saved.summary.files,
                megabytes(saved.summary.bytes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    // A failure while choosing the backup has no dialog to show in yet.
    if (restore.step == null) restore.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    RestoreDialogs(restore)
}

@Composable
private fun RestoreDialogs(presenter: RestorePresenter) {
    when (val step = presenter.step) {
        is RestoreStep.Confirm -> {
            RestoreDialog(step, presenter, restoring = false)
        }

        is RestoreStep.Restoring -> {
            // The confirmation stays up while unpacking, so the pilot sees what is being restored.
            RestoreDialog(step.confirm, presenter, restoring = true)
        }

        is RestoreStep.Restored -> {
            RestartDialog(
                stringResource(Res.string.restore_restart_body, step.folder, presenter.currentFolder),
                presenter::restart,
            )
        }

        null -> {}
    }
}

@Composable
private fun RestoreDialog(
    confirm: RestoreStep.Confirm,
    presenter: RestorePresenter,
    restoring: Boolean,
) {
    val preview = confirm.preview
    AppAlertDialog(
        onDismissRequest = { if (!restoring) presenter.cancel() },
        title = { Text(stringResource(Res.string.restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        Res.string.restore_made,
                        stringResource(
                            Res.string.flight_date_time,
                            preview.madeAt.date.toDisplay(),
                            preview.madeAt.time.toDisplay(),
                        ),
                        preview.appVersion,
                    ),
                )
                Text(
                    pluralStringResource(
                        Res.plurals.backup_saved_content,
                        preview.summary.files,
                        preview.summary.files,
                        megabytes(preview.summary.bytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (preview.madeByNewerVersion) {
                    StatusBadge(stringResource(Res.string.restore_newer_badge), Severity.Warning)
                    Text(stringResource(Res.string.restore_newer), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(Res.string.restore_how, presenter.currentFolder))
                if (restoring) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(Res.string.restore_in_progress))
                    }
                }
                presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = presenter::chooseFolder, enabled = !restoring) {
                Text(stringResource(Res.string.restore_choose_folder))
            }
        },
        dismissButton = {
            TextButton(onClick = presenter::cancel, enabled = !restoring) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

// Rounded up to one, so a small logbook does not read as an empty backup.
private fun megabytes(bytes: Long): String {
    val rounded = (bytes / BYTES_PER_MB).roundToInt()
    return (if (bytes > 0 && rounded == 0) 1 else rounded).toString()
}
