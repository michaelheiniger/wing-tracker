package ch.qscqlmpa.wingtracker.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ch.qscqlmpa.wingtracker.ui.components.AppAlertDialog
import ch.qscqlmpa.wingtracker.ui.components.AppCard
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.sync_automatic
import ch.qscqlmpa.wingtracker.ui.resources.sync_automatic_hint
import ch.qscqlmpa.wingtracker.ui.resources.sync_close_title
import ch.qscqlmpa.wingtracker.ui.resources.sync_close_without
import ch.qscqlmpa.wingtracker.ui.resources.sync_continue
import ch.qscqlmpa.wingtracker.ui.resources.sync_in_progress
import ch.qscqlmpa.wingtracker.ui.resources.sync_resolve
import ch.qscqlmpa.wingtracker.ui.resources.sync_skip
import ch.qscqlmpa.wingtracker.ui.resources.sync_start_running
import ch.qscqlmpa.wingtracker.ui.resources.sync_start_stopped
import org.jetbrains.compose.resources.stringResource

/** Stands in for the logbook while the start syncs, so nothing shows that the sync is about to change. */
@Composable
fun StartSyncScreen(
    presenter: AutoSyncPresenter,
    conflicts: ConflictPresenter,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        AppCard(modifier = Modifier.widthIn(max = 480.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (val start = presenter.start) {
                    StartSync.Running, StartSync.Done -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(Res.string.sync_start_running),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        TextButton(onClick = presenter::skip) { Text(stringResource(Res.string.sync_skip)) }
                    }

                    is StartSync.Stopped -> {
                        Text(
                            stringResource(Res.string.sync_start_stopped),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        start.outcome?.let { Text(it.text()) }
                        start.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (start.outcome != null) {
                                // Once resolved, the start goes on with what was exchanged.
                                Button(onClick = { conflicts.open { presenter.continueAfterStop() } }) {
                                    Text(stringResource(Res.string.sync_resolve))
                                }
                            }
                            TextButton(onClick = presenter::continueAfterStop) {
                                Text(stringResource(Res.string.sync_continue))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Not dismissible: the window is closing, and the only way out besides waiting is to close without syncing. */
@Composable
fun CloseSyncDialog(presenter: AutoSyncPresenter) {
    AppAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(Res.string.sync_close_title)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(Res.string.sync_in_progress))
            }
        },
        confirmButton = {
            TextButton(onClick = presenter::closeWithoutSync) { Text(stringResource(Res.string.sync_close_without)) }
        },
    )
}

@Composable
internal fun AutomaticSyncSwitch(presenter: SyncPresenter) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Switch(checked = presenter.status?.automatic == true, onCheckedChange = presenter::changeAutomatic)
        Column {
            Text(stringResource(Res.string.sync_automatic))
            Text(
                stringResource(Res.string.sync_automatic_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
