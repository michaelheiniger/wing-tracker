package ch.qscqlmpa.wingtracker.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import ch.qscqlmpa.wingtracker.backup.CloudBackup
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.components.AppAlertDialog
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.backup_cancel_upload
import ch.qscqlmpa.wingtracker.ui.resources.backup_cleanup_failed
import ch.qscqlmpa.wingtracker.ui.resources.backup_create
import ch.qscqlmpa.wingtracker.ui.resources.backup_deleted_old
import ch.qscqlmpa.wingtracker.ui.resources.backup_in_progress
import ch.qscqlmpa.wingtracker.ui.resources.backup_last
import ch.qscqlmpa.wingtracker.ui.resources.backup_never
import ch.qscqlmpa.wingtracker.ui.resources.backup_restore
import ch.qscqlmpa.wingtracker.ui.resources.backup_restore_cloud
import ch.qscqlmpa.wingtracker.ui.resources.backup_restore_file
import ch.qscqlmpa.wingtracker.ui.resources.backup_save_file
import ch.qscqlmpa.wingtracker.ui.resources.backup_saved
import ch.qscqlmpa.wingtracker.ui.resources.backup_saved_content
import ch.qscqlmpa.wingtracker.ui.resources.backup_upload
import ch.qscqlmpa.wingtracker.ui.resources.backup_uploaded
import ch.qscqlmpa.wingtracker.ui.resources.backup_uploading
import ch.qscqlmpa.wingtracker.ui.resources.flight_date_time
import ch.qscqlmpa.wingtracker.ui.resources.restore_choose_folder
import ch.qscqlmpa.wingtracker.ui.resources.restore_cloud_choose
import ch.qscqlmpa.wingtracker.ui.resources.restore_cloud_empty
import ch.qscqlmpa.wingtracker.ui.resources.restore_cloud_size
import ch.qscqlmpa.wingtracker.ui.resources.restore_cloud_title
import ch.qscqlmpa.wingtracker.ui.resources.restore_downloading
import ch.qscqlmpa.wingtracker.ui.resources.restore_how
import ch.qscqlmpa.wingtracker.ui.resources.restore_in_progress
import ch.qscqlmpa.wingtracker.ui.resources.restore_made
import ch.qscqlmpa.wingtracker.ui.resources.restore_newer
import ch.qscqlmpa.wingtracker.ui.resources.restore_newer_badge
import ch.qscqlmpa.wingtracker.ui.resources.restore_restart_body
import ch.qscqlmpa.wingtracker.ui.resources.restore_title
import ch.qscqlmpa.wingtracker.ui.settings.RestartDialog
import ch.qscqlmpa.wingtracker.ui.theme.extraColors
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.StringResource
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
        presenter.lastMadeAt?.let {
            stringResource(
                Res.string.backup_last,
                stringResource(Res.string.flight_date_time, it.date.toDisplay(), it.time.toDisplay()),
            )
        } ?: stringResource(Res.string.backup_never),
        style = MaterialTheme.typography.bodyLarge,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BackupButtons(presenter)
        val canRestore = !presenter.busy && restore.step == null
        if (presenter.cloud == null) {
            OutlinedButton(onClick = restore::start, enabled = canRestore) {
                Text(stringResource(Res.string.backup_restore))
            }
        } else {
            OutlinedButton(onClick = restore::start, enabled = canRestore) {
                Text(stringResource(Res.string.backup_restore_file))
            }
            OutlinedButton(onClick = restore::startFromCloud, enabled = canRestore) {
                Text(stringResource(Res.string.backup_restore_cloud))
            }
        }
        if (presenter.busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                stringResource(if (presenter.uploading) Res.string.backup_uploading else Res.string.backup_in_progress),
            )
        }
    }
    presenter.outcome?.let { BackupOutcomeText(it) }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    // A failure while choosing the backup has no dialog to show in yet.
    if (restore.step == null) restore.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    RestoreDialogs(restore)
}

/** The ways to back up: a file only, or a file or the bucket once one is set up (ADR 0032). */
@Composable
internal fun BackupButtons(
    presenter: BackupPresenter,
    textButtons: Boolean = false,
) {
    val enabled = !presenter.busy
    if (presenter.cloud == null) {
        ActionButton(textButtons, presenter::create, enabled, Res.string.backup_create)
    } else {
        ActionButton(textButtons, presenter::create, enabled, Res.string.backup_save_file)
        // In place of the upload button, where the pilot's eyes already are.
        if (presenter.uploading) {
            ActionButton(textButtons, presenter::cancelUpload, enabled = true, Res.string.backup_cancel_upload)
        } else {
            ActionButton(textButtons, presenter::upload, enabled, Res.string.backup_upload)
        }
    }
}

@Composable
private fun ActionButton(
    text: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    label: StringResource,
) {
    if (text) {
        TextButton(onClick = onClick, enabled = enabled) { Text(stringResource(label)) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) { Text(stringResource(label)) }
    }
}

@Composable
private fun BackupOutcomeText(outcome: BackupOutcome) {
    val summary =
        when (outcome) {
            is BackupOutcome.Saved -> {
                // The path is selectable, so the pilot can find the file again or copy it elsewhere.
                Text(stringResource(Res.string.backup_saved, outcome.path))
                outcome.summary
            }

            is BackupOutcome.Uploaded -> {
                Text(stringResource(Res.string.backup_uploaded, outcome.upload.bucket, outcome.upload.key))
                outcome.upload.summary
            }
        }
    Text(
        pluralStringResource(Res.plurals.backup_saved_content, summary.files, summary.files, megabytes(summary.bytes)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val upload = (outcome as? BackupOutcome.Uploaded)?.upload ?: return
    if (upload.deleted > 0) {
        Text(
            pluralStringResource(Res.plurals.backup_deleted_old, upload.deleted, upload.deleted),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    upload.cleanupProblem?.let {
        Text(
            stringResource(Res.string.backup_cleanup_failed, Failure(it.failureKind()).message()),
            color = MaterialTheme.extraColors.warning,
        )
    }
}

@Composable
private fun RestoreDialogs(presenter: RestorePresenter) {
    when (val step = presenter.step) {
        is RestoreStep.ChooseCloud -> {
            CloudBackupsDialog(step.backups, downloading = null, presenter)
        }

        is RestoreStep.Downloading -> {
            CloudBackupsDialog(listOf(step.backup), downloading = step.backup, presenter)
        }

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

/** The backups in the bucket, newest first, each restored with its own button (ADR 0032). */
@Composable
private fun CloudBackupsDialog(
    backups: List<CloudBackup>,
    downloading: CloudBackup?,
    presenter: RestorePresenter,
) {
    AppAlertDialog(
        onDismissRequest = { if (downloading == null) presenter.cancel() },
        title = { Text(stringResource(Res.string.restore_cloud_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            ) {
                if (backups.isEmpty()) Text(stringResource(Res.string.restore_cloud_empty))
                backups.forEach { backup ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(
                                    Res.string.flight_date_time,
                                    backup.madeAt.date.toDisplay(),
                                    backup.madeAt.time.toDisplay(),
                                ),
                            )
                            Text(
                                stringResource(Res.string.restore_cloud_size, megabytes(backup.bytes)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (downloading == backup) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(Res.string.restore_downloading))
                        } else {
                            TextButton(onClick = { presenter.chooseCloudBackup(backup) }) {
                                Text(stringResource(Res.string.restore_cloud_choose))
                            }
                        }
                    }
                }
                presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            // While downloading, it stops the download and goes back to the list.
            TextButton(onClick = if (downloading == null) presenter::cancel else presenter::cancelDownload) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
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
