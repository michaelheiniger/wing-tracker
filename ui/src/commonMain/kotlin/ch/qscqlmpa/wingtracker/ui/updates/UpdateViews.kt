package ch.qscqlmpa.wingtracker.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.icons.UpdateIcon
import ch.qscqlmpa.wingtracker.ui.isWorthRetrying
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_try_again
import ch.qscqlmpa.wingtracker.ui.resources.update_available
import ch.qscqlmpa.wingtracker.ui.resources.update_badge
import ch.qscqlmpa.wingtracker.ui.resources.update_check_at_startup
import ch.qscqlmpa.wingtracker.ui.resources.update_check_at_startup_hint
import ch.qscqlmpa.wingtracker.ui.resources.update_check_now
import ch.qscqlmpa.wingtracker.ui.resources.update_checking
import ch.qscqlmpa.wingtracker.ui.resources.update_close
import ch.qscqlmpa.wingtracker.ui.resources.update_download
import ch.qscqlmpa.wingtracker.ui.resources.update_install
import ch.qscqlmpa.wingtracker.ui.resources.update_later
import ch.qscqlmpa.wingtracker.ui.resources.update_no_notes
import ch.qscqlmpa.wingtracker.ui.resources.update_not_applicable
import ch.qscqlmpa.wingtracker.ui.resources.update_not_installable
import ch.qscqlmpa.wingtracker.ui.resources.update_release_title
import ch.qscqlmpa.wingtracker.ui.resources.update_skip
import ch.qscqlmpa.wingtracker.ui.resources.update_skipped
import ch.qscqlmpa.wingtracker.ui.resources.update_title
import ch.qscqlmpa.wingtracker.ui.resources.update_up_to_date
import ch.qscqlmpa.wingtracker.ui.resources.update_whats_new
import ch.qscqlmpa.wingtracker.ui.resources.update_whats_new_title
import ch.qscqlmpa.wingtracker.updates.AvailableUpdate
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import org.jetbrains.compose.resources.stringResource

/** Announces an update at the top of the main window, until the pilot postpones or skips it. */
@Composable
fun UpdateBanner(
    presenter: UpdatePresenter,
    installer: UpdateInstallPresenter,
    modifier: Modifier = Modifier,
) {
    val update = presenter.update ?: return
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(UpdateIcon, contentDescription = null)
            Text(
                stringResource(Res.string.update_available, update.latest.version.toString()),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = presenter::openWhatsNew) { Text(stringResource(Res.string.update_whats_new)) }
            if (update.installableFromApp) {
                TextButton(onClick = { installer.install(update) }) { Text(stringResource(Res.string.update_install)) }
            } else {
                // Without published checksums the app refuses to install; the release page still has the installer.
                TextButton(onClick = { uriHandler.openUri(update.latest.pageUrl) }) {
                    Text(stringResource(Res.string.update_download))
                }
            }
            TextButton(onClick = presenter::skip) { Text(stringResource(Res.string.update_skip)) }
            TextButton(onClick = presenter::postpone) { Text(stringResource(Res.string.update_later)) }
        }
    }
}

/** The update part of the settings: the startup check, a manual check and what it found. */
@Composable
fun UpdatesSection(
    presenter: UpdatePresenter,
    installer: UpdateInstallPresenter,
) {
    Text(
        stringResource(Res.string.update_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Switch(checked = presenter.checkAtStartup, onCheckedChange = presenter::changeCheckAtStartup)
        Column {
            Text(stringResource(Res.string.update_check_at_startup))
            Text(
                stringResource(Res.string.update_check_at_startup_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = presenter::checkNow, enabled = presenter.manualCheck != ManualCheck.Checking) {
            val failed = (presenter.manualCheck as? ManualCheck.Failed)?.failure
            Text(
                stringResource(
                    if (failed?.isWorthRetrying ==
                        true
                    ) {
                        Res.string.action_try_again
                    } else {
                        Res.string.update_check_now
                    },
                ),
            )
        }
        ManualCheckOutcome(presenter.manualCheck)
    }
    presenter.update?.let { FoundUpdate(it, presenter, installer) }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun ManualCheckOutcome(check: ManualCheck) {
    when (check) {
        ManualCheck.Checking -> Text(stringResource(Res.string.update_checking))
        ManualCheck.UpToDate -> Text(stringResource(Res.string.update_up_to_date))
        ManualCheck.NotApplicable -> Text(stringResource(Res.string.update_not_applicable))
        is ManualCheck.Failed -> Text(check.failure.message(), color = MaterialTheme.colorScheme.error)
        ManualCheck.Idle, ManualCheck.Found -> Unit
    }
}

@Composable
private fun FoundUpdate(
    update: AvailableUpdate,
    presenter: UpdatePresenter,
    installer: UpdateInstallPresenter,
) {
    val uriHandler = LocalUriHandler.current
    val version = update.latest.version.toString()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (update.skipped) {
            Text(stringResource(Res.string.update_skipped, version))
        } else {
            UpdateBadge(version)
        }
        TextButton(onClick = presenter::openWhatsNew) { Text(stringResource(Res.string.update_whats_new)) }
        InstallOrDownload(update, onInstall = { installer.install(update) }, onOpenPage = uriHandler::openUri)
    }
    if (!update.installableFromApp) {
        Text(
            stringResource(Res.string.update_not_installable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InstallOrDownload(
    update: AvailableUpdate,
    onInstall: () -> Unit,
    onOpenPage: (String) -> Unit,
) {
    if (update.installableFromApp) {
        Button(onClick = onInstall) { Text(stringResource(Res.string.update_install)) }
    } else {
        Button(onClick = { onOpenPage(update.latest.pageUrl) }) { Text(stringResource(Res.string.update_download)) }
    }
}

/** Icon and text, so the state never relies on colour alone (ADR 0019). */
@Composable
fun UpdateBadge(version: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(UpdateIcon, contentDescription = null)
            Text(stringResource(Res.string.update_badge, version), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Every release since the installed one, newest first, so nothing that changed goes unseen. */
@Composable
fun WhatsNewDialog(
    presenter: UpdatePresenter,
    installer: UpdateInstallPresenter,
) {
    val update = presenter.update
    if (!presenter.showsWhatsNew || update == null) return
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = presenter::closeWhatsNew,
        title = { Text(stringResource(Res.string.update_whats_new_title, update.latest.version.toString())) },
        text = {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!update.installableFromApp) Text(stringResource(Res.string.update_not_installable))
                update.releases.forEachIndexed { index, release ->
                    if (index > 0) HorizontalDivider()
                    Text(
                        stringResource(Res.string.update_release_title, release.version.toString()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (release.notes.isBlank()) {
                        Text(stringResource(Res.string.update_no_notes))
                    } else {
                        SelectionContainer { ReleaseNotes(release.notes) }
                    }
                }
            }
        },
        confirmButton = {
            InstallOrDownload(
                update,
                onInstall = {
                    presenter.closeWhatsNew()
                    installer.install(update)
                },
                onOpenPage = uriHandler::openUri,
            )
        },
        dismissButton = {
            TextButton(onClick = presenter::closeWhatsNew) { Text(stringResource(Res.string.update_close)) }
        },
    )
}

// The changelog's own headings sit below the release title, so they are sized as subheadings, not page titles.
@Composable
private fun ReleaseNotes(notes: String) {
    val typography = MaterialTheme.typography
    Markdown(
        content = notes,
        modifier = Modifier.fillMaxWidth(),
        typography =
            markdownTypography(
                h1 = typography.titleMedium,
                h2 = typography.titleMedium,
                h3 = typography.titleSmall,
                h4 = typography.titleSmall,
                h5 = typography.titleSmall,
                h6 = typography.titleSmall,
                text = typography.bodyMedium,
                paragraph = typography.bodyMedium,
                bullet = typography.bodyMedium,
                list = typography.bodyMedium,
            ),
    )
}
