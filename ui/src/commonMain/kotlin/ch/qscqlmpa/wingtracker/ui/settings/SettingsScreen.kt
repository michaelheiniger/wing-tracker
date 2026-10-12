package ch.qscqlmpa.wingtracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.settings.ThemeMode
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.reminders.ExportOutcome
import ch.qscqlmpa.wingtracker.ui.reminders.ReminderSettingsField
import ch.qscqlmpa.wingtracker.ui.reminders.ReminderSettingsPresenter
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.about_changelog
import ch.qscqlmpa.wingtracker.ui.resources.about_close
import ch.qscqlmpa.wingtracker.ui.resources.about_open
import ch.qscqlmpa.wingtracker.ui.resources.about_title
import ch.qscqlmpa.wingtracker.ui.resources.about_version
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.data_change_body
import ch.qscqlmpa.wingtracker.ui.resources.data_change_has_data
import ch.qscqlmpa.wingtracker.ui.resources.data_change_move
import ch.qscqlmpa.wingtracker.ui.resources.data_change_move_hint
import ch.qscqlmpa.wingtracker.ui.resources.data_change_title
import ch.qscqlmpa.wingtracker.ui.resources.data_change_use
import ch.qscqlmpa.wingtracker.ui.resources.data_change_use_existing
import ch.qscqlmpa.wingtracker.ui.resources.data_restart_body
import ch.qscqlmpa.wingtracker.ui.resources.data_restart_now
import ch.qscqlmpa.wingtracker.ui.resources.data_restart_title
import ch.qscqlmpa.wingtracker.ui.resources.field_lead_days
import ch.qscqlmpa.wingtracker.ui.resources.field_lead_hours
import ch.qscqlmpa.wingtracker.ui.resources.scale_automatic
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_empty
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_export
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_exported
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_hint
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_title
import ch.qscqlmpa.wingtracker.ui.resources.settings_data_change
import ch.qscqlmpa.wingtracker.ui.resources.settings_data_default
import ch.qscqlmpa.wingtracker.ui.resources.settings_data_hint
import ch.qscqlmpa.wingtracker.ui.resources.settings_data_open
import ch.qscqlmpa.wingtracker.ui.resources.settings_data_title
import ch.qscqlmpa.wingtracker.ui.resources.settings_reminders_hint
import ch.qscqlmpa.wingtracker.ui.resources.settings_reminders_title
import ch.qscqlmpa.wingtracker.ui.resources.settings_save
import ch.qscqlmpa.wingtracker.ui.resources.settings_saved
import ch.qscqlmpa.wingtracker.ui.resources.settings_scale_hint
import ch.qscqlmpa.wingtracker.ui.resources.settings_scale_title
import ch.qscqlmpa.wingtracker.ui.resources.settings_theme
import ch.qscqlmpa.wingtracker.ui.resources.settings_title
import ch.qscqlmpa.wingtracker.ui.resources.theme_dark
import ch.qscqlmpa.wingtracker.ui.resources.theme_light
import ch.qscqlmpa.wingtracker.ui.resources.theme_system
import ch.qscqlmpa.wingtracker.ui.theme.InterfaceScalePreference
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    themePreference: ThemePreference,
    scalePreference: InterfaceScalePreference,
    reminders: ReminderSettingsPresenter,
    dataLocation: DataLocationPresenter,
    appVersion: String,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(Res.string.settings_theme), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themePreference.mode == mode,
                    onClick = { themePreference.mode = mode },
                    label = { Text(stringResource(mode.label())) },
                )
            }
        }
        ScaleSection(scalePreference)
        RemindersSection(reminders)
        CalendarSection(reminders)
        DataLocationSection(dataLocation)
        AboutSection(appVersion)
        reminders.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun DataLocationSection(presenter: DataLocationPresenter) {
    Text(
        stringResource(Res.string.settings_data_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text(
        stringResource(Res.string.settings_data_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Selectable, so the pilot can copy the path into a terminal or a backup tool.
    SelectionContainer { Text(presenter.path, style = MaterialTheme.typography.bodyLarge) }
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
    if (presenter.restartRequired) RestartDialog(presenter)
}

@Composable
private fun ChangeDialog(
    proposal: FolderProposal,
    presenter: DataLocationPresenter,
) {
    AlertDialog(
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

// Not dismissible: carrying on would write to the old folder while the new one is already chosen.
@Composable
private fun RestartDialog(presenter: DataLocationPresenter) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.data_restart_title)) },
        text = { Text(stringResource(Res.string.data_restart_body)) },
        confirmButton = { Button(onClick = presenter::restart) { Text(stringResource(Res.string.data_restart_now)) } },
    )
}

private fun ThemeMode.label() =
    when (this) {
        ThemeMode.System -> Res.string.theme_system
        ThemeMode.Light -> Res.string.theme_light
        ThemeMode.Dark -> Res.string.theme_dark
    }

@Composable
private fun RemindersSection(presenter: ReminderSettingsPresenter) {
    val input = presenter.input
    Text(
        stringResource(Res.string.settings_reminders_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text(
        stringResource(Res.string.settings_reminders_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = input.days,
            onValueChange = { v -> presenter.change { it.copy(days = v) } },
            label = { Text(stringResource(Res.string.field_lead_days)) },
            isError = ReminderSettingsField.Days in presenter.errors,
            supportingText = { presenter.errors[ReminderSettingsField.Days]?.let { Text(it.message()) } },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = input.hours,
            onValueChange = { v -> presenter.change { it.copy(hours = v) } },
            label = { Text(stringResource(Res.string.field_lead_hours)) },
            isError = ReminderSettingsField.Hours in presenter.errors,
            supportingText = { presenter.errors[ReminderSettingsField.Hours]?.let { Text(it.message()) } },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = presenter::save) { Text(stringResource(Res.string.settings_save)) }
        if (presenter.saved) Text(stringResource(Res.string.settings_saved), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CalendarSection(presenter: ReminderSettingsPresenter) {
    Text(
        stringResource(Res.string.settings_calendar_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text(
        stringResource(Res.string.settings_calendar_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = presenter::exportCalendar) { Text(stringResource(Res.string.settings_calendar_export)) }
    val outcome = presenter.export
    if (outcome is ExportOutcome.Saved) {
        Text(stringResource(Res.string.settings_calendar_exported, outcome.fileName))
    } else if (outcome == ExportOutcome.NothingToExport) {
        Text(stringResource(Res.string.settings_calendar_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScaleSection(preference: InterfaceScalePreference) {
    Text(
        stringResource(Res.string.settings_scale_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text(
        stringResource(Res.string.settings_scale_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = preference.percent == null,
            onClick = { preference.percent = null },
            label = { Text(stringResource(Res.string.scale_automatic)) },
        )
        InterfaceScalePreference.CHOICES.forEach { percent ->
            FilterChip(
                selected = preference.percent == percent,
                onClick = { preference.percent = percent },
                label = { Text("$percent %") },
            )
        }
    }
}

// The published changelog on the main branch, so it also shows what changed in releases newer than the installed one.
private const val CHANGELOG_URL = "https://github.com/michaelheiniger/wing-tracker/blob/main/CHANGELOG.md"

@Composable
private fun AboutSection(appVersion: String) {
    var open by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { open = true }, modifier = Modifier.padding(top = 16.dp)) {
        Text(stringResource(Res.string.about_open))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(Res.string.about_title)) },
            // Selectable, so the version can be pasted into a bug report.
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer { Text(stringResource(Res.string.about_version, appVersion)) }
                    TextButton(onClick = { uriHandler.openUri(CHANGELOG_URL) }) {
                        Text(stringResource(Res.string.about_changelog))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(Res.string.about_close)) } },
        )
    }
}
