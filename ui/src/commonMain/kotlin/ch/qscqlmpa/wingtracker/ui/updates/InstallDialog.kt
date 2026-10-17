package ch.qscqlmpa.wingtracker.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.components.AppAlertDialog
import ch.qscqlmpa.wingtracker.ui.isWorthRetrying
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_try_again
import ch.qscqlmpa.wingtracker.ui.resources.update_cancel
import ch.qscqlmpa.wingtracker.ui.resources.update_cancelled
import ch.qscqlmpa.wingtracker.ui.resources.update_close
import ch.qscqlmpa.wingtracker.ui.resources.update_downloaded_bytes
import ch.qscqlmpa.wingtracker.ui.resources.update_downloading
import ch.qscqlmpa.wingtracker.ui.resources.update_install_and_restart
import ch.qscqlmpa.wingtracker.ui.resources.update_install_title
import ch.qscqlmpa.wingtracker.ui.resources.update_installed
import ch.qscqlmpa.wingtracker.ui.resources.update_installing
import ch.qscqlmpa.wingtracker.ui.resources.update_installs_on_restart
import ch.qscqlmpa.wingtracker.ui.resources.update_manual
import ch.qscqlmpa.wingtracker.ui.resources.update_manual_command
import ch.qscqlmpa.wingtracker.ui.resources.update_open_release_page
import ch.qscqlmpa.wingtracker.ui.resources.update_restart_later
import ch.qscqlmpa.wingtracker.ui.resources.update_restart_now
import ch.qscqlmpa.wingtracker.ui.resources.update_show_in_folder
import ch.qscqlmpa.wingtracker.updates.AvailableUpdate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val BYTES_PER_MB = 1024.0 * 1024.0

/** Follows installing [update] from download to restart; it cannot be dismissed while the system is installing. */
@Composable
fun InstallDialog(
    presenter: UpdateInstallPresenter,
    update: AvailableUpdate?,
) {
    val state = presenter.state
    if (state is InstallState.Idle || update == null) return
    val version = update.latest.version.toString()
    val uriHandler = LocalUriHandler.current
    AppAlertDialog(
        onDismissRequest = presenter::close,
        title = { Text(stringResource(Res.string.update_install_title, version)) },
        text = {
            Column(modifier = Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InstallStateContent(state, version)
            }
        },
        confirmButton = {
            when (state) {
                is InstallState.Downloading -> {
                    TextButton(onClick = presenter::cancelDownload) { Text(stringResource(Res.string.update_cancel)) }
                }

                InstallState.Installed -> {
                    Button(onClick = presenter::restart) { Text(stringResource(Res.string.update_restart_now)) }
                }

                InstallState.InstallsOnRestart -> {
                    Button(onClick = presenter::restart) {
                        Text(stringResource(Res.string.update_install_and_restart))
                    }
                }

                is InstallState.Cancelled -> {
                    Button(onClick = presenter::retry) { Text(stringResource(Res.string.action_try_again)) }
                }

                is InstallState.Manual -> {
                    Button(onClick = presenter::showInFolder) {
                        Text(stringResource(Res.string.update_show_in_folder))
                    }
                }

                is InstallState.Failed -> {
                    if (state.failure.isWorthRetrying) {
                        Button(
                            onClick = { presenter.install(update) },
                        ) { Text(stringResource(Res.string.action_try_again)) }
                    } else {
                        Button(onClick = { uriHandler.openUri(update.latest.pageUrl) }) {
                            Text(stringResource(Res.string.update_open_release_page))
                        }
                    }
                }

                InstallState.Installing, InstallState.Idle -> {}
            }
        },
        dismissButton = {
            when (state) {
                InstallState.Installed, InstallState.InstallsOnRestart -> {
                    TextButton(onClick = presenter::close) { Text(stringResource(Res.string.update_restart_later)) }
                }

                is InstallState.Cancelled, is InstallState.Manual, is InstallState.Failed -> {
                    TextButton(onClick = presenter::close) { Text(stringResource(Res.string.update_close)) }
                }

                is InstallState.Downloading, InstallState.Installing, InstallState.Idle -> {}
            }
        },
    )
}

@Composable
private fun InstallStateContent(
    state: InstallState,
    version: String,
) {
    when (state) {
        is InstallState.Downloading -> {
            Text(stringResource(Res.string.update_downloading))
            val fraction = state.fraction
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            state.total?.let { total ->
                Text(
                    stringResource(Res.string.update_downloaded_bytes, megabytes(state.received), megabytes(total)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        InstallState.Installing -> {
            Text(stringResource(Res.string.update_installing))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        InstallState.Installed -> {
            Text(stringResource(Res.string.update_installed, version))
        }

        InstallState.InstallsOnRestart -> {
            Text(stringResource(Res.string.update_installs_on_restart, version))
        }

        is InstallState.Cancelled -> {
            Text(stringResource(Res.string.update_cancelled))
        }

        is InstallState.Manual -> {
            Text(stringResource(Res.string.update_manual))
            // Selectable, so the path and the command can be pasted into a terminal.
            Text(state.path, fontFamily = FontFamily.Monospace)
            state.command?.let { command ->
                Text(stringResource(Res.string.update_manual_command))
                Text(command, fontFamily = FontFamily.Monospace)
            }
        }

        is InstallState.Failed -> {
            Text(state.failure.message(), color = MaterialTheme.colorScheme.error)
        }

        InstallState.Idle -> {}
    }
}

private fun megabytes(bytes: Long): String = (bytes / BYTES_PER_MB).roundToInt().toString()
