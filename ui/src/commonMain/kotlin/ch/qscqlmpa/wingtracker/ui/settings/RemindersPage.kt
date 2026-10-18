package ch.qscqlmpa.wingtracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.reminders.ExportOutcome
import ch.qscqlmpa.wingtracker.ui.reminders.ReminderSettingsField
import ch.qscqlmpa.wingtracker.ui.reminders.ReminderSettingsPresenter
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.field_backup_days
import ch.qscqlmpa.wingtracker.ui.resources.field_lead_days
import ch.qscqlmpa.wingtracker.ui.resources.field_lead_hours
import ch.qscqlmpa.wingtracker.ui.resources.settings_backup_reminder
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_empty
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_export
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_exported
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_hint
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_title
import ch.qscqlmpa.wingtracker.ui.resources.settings_reminders_hint
import ch.qscqlmpa.wingtracker.ui.resources.settings_reminders_title
import ch.qscqlmpa.wingtracker.ui.resources.settings_save
import ch.qscqlmpa.wingtracker.ui.resources.settings_saved
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RemindersPage(presenter: ReminderSettingsPresenter) {
    SettingsGroup(Res.string.settings_reminders_title, Res.string.settings_reminders_hint) {
        LeadTimes(presenter)
    }
    SettingsGroup(Res.string.settings_calendar_title, Res.string.settings_calendar_hint) {
        Calendar(presenter)
    }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun LeadTimes(presenter: ReminderSettingsPresenter) {
    val input = presenter.input
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Switch(
                checked = input.backupEnabled,
                onCheckedChange = { on -> presenter.change { it.copy(backupEnabled = on) } },
            )
            Text(stringResource(Res.string.settings_backup_reminder))
        }
        OutlinedTextField(
            value = input.backupDays,
            onValueChange = { v -> presenter.change { it.copy(backupDays = v) } },
            label = { Text(stringResource(Res.string.field_backup_days)) },
            enabled = input.backupEnabled,
            isError = ReminderSettingsField.BackupDays in presenter.errors,
            supportingText = { presenter.errors[ReminderSettingsField.BackupDays]?.let { Text(it.message()) } },
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
private fun Calendar(presenter: ReminderSettingsPresenter) {
    OutlinedButton(onClick = presenter::exportCalendar) { Text(stringResource(Res.string.settings_calendar_export)) }
    val outcome = presenter.export
    if (outcome is ExportOutcome.Saved) {
        Text(stringResource(Res.string.settings_calendar_exported, outcome.fileName))
    } else if (outcome == ExportOutcome.NothingToExport) {
        Text(stringResource(Res.string.settings_calendar_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
