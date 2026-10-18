package ch.qscqlmpa.wingtracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.components.AppAlertDialog
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.data_change_body
import ch.qscqlmpa.wingtracker.ui.resources.data_change_has_data
import ch.qscqlmpa.wingtracker.ui.resources.data_change_move
import ch.qscqlmpa.wingtracker.ui.resources.data_change_move_hint
import ch.qscqlmpa.wingtracker.ui.resources.data_change_title
import ch.qscqlmpa.wingtracker.ui.resources.data_change_use
import ch.qscqlmpa.wingtracker.ui.resources.data_change_use_existing
import ch.qscqlmpa.wingtracker.ui.resources.data_restart_body
import ch.qscqlmpa.wingtracker.ui.resources.settings_data_change
import ch.qscqlmpa.wingtracker.ui.resources.settings_data_default
import ch.qscqlmpa.wingtracker.ui.resources.settings_data_hint
import ch.qscqlmpa.wingtracker.ui.resources.settings_data_open
import ch.qscqlmpa.wingtracker.ui.resources.settings_data_title
import ch.qscqlmpa.wingtracker.ui.resources.settings_log_hint
import ch.qscqlmpa.wingtracker.ui.resources.settings_log_open
import ch.qscqlmpa.wingtracker.ui.resources.settings_log_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FilesPage(
    dataLocation: DataLocationPresenter,
    logLocation: LogLocationPresenter,
) {
    SettingsGroup(Res.string.settings_data_title, Res.string.settings_data_hint) { DataLocation(dataLocation) }
    SettingsGroup(Res.string.settings_log_title, Res.string.settings_log_hint) { LogLocation(logLocation) }
}

@Composable
private fun DataLocation(presenter: DataLocationPresenter) {
    // Selectable, so the pilot can copy the path into a terminal or a backup tool.
    Text(presenter.path, style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = presenter::open) { Text(stringResource(Res.string.settings_data_open)) }
        OutlinedButton(onClick = presenter::choose, enabled = !presenter.busy) {
            Text(stringResource(Res.string.settings_data_change))
        }
        if (!presenter.isDefault) {
            TextButton(onClick = presenter::useDefault, enabled = !presenter.busy) {
                Text(stringResource(Res.string.settings_data_default))
            }
        }
    }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    presenter.proposal?.let { ChangeDialog(it, presenter) }
    if (presenter.restartRequired) RestartDialog(stringResource(Res.string.data_restart_body), presenter::restart)
}

@Composable
private fun LogLocation(presenter: LogLocationPresenter) {
    // Selectable, so the pilot can paste the path into a bug report or a mail attachment dialog.
    Text(presenter.path, style = MaterialTheme.typography.bodyLarge)
    OutlinedButton(onClick = presenter::open) { Text(stringResource(Res.string.settings_log_open)) }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun ChangeDialog(
    proposal: FolderProposal,
    presenter: DataLocationPresenter,
) {
    AppAlertDialog(
        onDismissRequest = presenter::cancel,
        title = { Text(stringResource(Res.string.data_change_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.data_change_body, proposal.folder))
                Text(
                    stringResource(
                        if (proposal.canMove) Res.string.data_change_move_hint else Res.string.data_change_has_data,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (proposal.canMove) {
                Button(
                    onClick = { presenter.confirm(moveData = true) },
                ) { Text(stringResource(Res.string.data_change_move)) }
            } else {
                Button(onClick = { presenter.confirm(moveData = false) }) {
                    Text(stringResource(Res.string.data_change_use_existing))
                }
            }
        },
        dismissButton = {
            Row {
                if (proposal.canMove) {
                    TextButton(onClick = { presenter.confirm(moveData = false) }) {
                        Text(stringResource(Res.string.data_change_use))
                    }
                }
                TextButton(onClick = presenter::cancel) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}
