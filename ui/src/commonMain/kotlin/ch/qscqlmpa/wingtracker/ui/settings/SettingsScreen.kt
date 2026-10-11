package ch.qscqlmpa.wingtracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.settings.ThemeMode
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.reminders.ExportOutcome
import ch.qscqlmpa.wingtracker.ui.reminders.ReminderSettingsField
import ch.qscqlmpa.wingtracker.ui.reminders.ReminderSettingsPresenter
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.field_lead_days
import ch.qscqlmpa.wingtracker.ui.resources.field_lead_hours
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_empty
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_export
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_exported
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_hint
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_title
import ch.qscqlmpa.wingtracker.ui.resources.settings_reminders_hint
import ch.qscqlmpa.wingtracker.ui.resources.settings_reminders_title
import ch.qscqlmpa.wingtracker.ui.resources.settings_save
import ch.qscqlmpa.wingtracker.ui.resources.settings_saved
import ch.qscqlmpa.wingtracker.ui.resources.settings_theme
import ch.qscqlmpa.wingtracker.ui.resources.settings_title
import ch.qscqlmpa.wingtracker.ui.resources.theme_dark
import ch.qscqlmpa.wingtracker.ui.resources.theme_light
import ch.qscqlmpa.wingtracker.ui.resources.theme_system
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    themePreference: ThemePreference,
    reminders: ReminderSettingsPresenter,
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
        RemindersSection(reminders)
        CalendarSection(reminders)
        reminders.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    }
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
