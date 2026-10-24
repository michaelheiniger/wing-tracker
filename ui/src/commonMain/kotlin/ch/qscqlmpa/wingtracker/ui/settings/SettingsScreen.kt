package ch.qscqlmpa.wingtracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.settings.ThemeMode
import ch.qscqlmpa.wingtracker.ui.backup.BackupPresenter
import ch.qscqlmpa.wingtracker.ui.backup.BackupSection
import ch.qscqlmpa.wingtracker.ui.backup.CloudStoragePresenter
import ch.qscqlmpa.wingtracker.ui.backup.CloudStorageSection
import ch.qscqlmpa.wingtracker.ui.backup.RestorePresenter
import ch.qscqlmpa.wingtracker.ui.components.AppCard
import ch.qscqlmpa.wingtracker.ui.icons.UpdateIcon
import ch.qscqlmpa.wingtracker.ui.reminders.ReminderSettingsPresenter
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.about_changelog
import ch.qscqlmpa.wingtracker.ui.resources.about_title
import ch.qscqlmpa.wingtracker.ui.resources.about_version
import ch.qscqlmpa.wingtracker.ui.resources.backup_hint
import ch.qscqlmpa.wingtracker.ui.resources.backup_title
import ch.qscqlmpa.wingtracker.ui.resources.cloud_hint
import ch.qscqlmpa.wingtracker.ui.resources.cloud_title
import ch.qscqlmpa.wingtracker.ui.resources.scale_automatic
import ch.qscqlmpa.wingtracker.ui.resources.settings_scale_hint
import ch.qscqlmpa.wingtracker.ui.resources.settings_scale_title
import ch.qscqlmpa.wingtracker.ui.resources.settings_theme
import ch.qscqlmpa.wingtracker.ui.resources.settings_title
import ch.qscqlmpa.wingtracker.ui.resources.sync_hint
import ch.qscqlmpa.wingtracker.ui.resources.sync_title
import ch.qscqlmpa.wingtracker.ui.resources.theme_dark
import ch.qscqlmpa.wingtracker.ui.resources.theme_light
import ch.qscqlmpa.wingtracker.ui.resources.theme_system
import ch.qscqlmpa.wingtracker.ui.resources.update_rail_badge
import ch.qscqlmpa.wingtracker.ui.resources.update_title
import ch.qscqlmpa.wingtracker.ui.sync.SyncPresenter
import ch.qscqlmpa.wingtracker.ui.sync.SyncSection
import ch.qscqlmpa.wingtracker.ui.theme.InterfaceScalePreference
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
import ch.qscqlmpa.wingtracker.ui.updates.UpdateBadge
import ch.qscqlmpa.wingtracker.ui.updates.UpdateInstallPresenter
import ch.qscqlmpa.wingtracker.ui.updates.UpdatePresenter
import ch.qscqlmpa.wingtracker.ui.updates.UpdatesSection
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val CATEGORY_LIST_WIDTH = 240.dp

// Wider fields and lines get hard to read on a large window.
private val PAGE_MAX_WIDTH = 760.dp

@Composable
fun SettingsScreen(
    category: SettingsCategory,
    onCategoryChange: (SettingsCategory) -> Unit,
    themePreference: ThemePreference,
    scalePreference: InterfaceScalePreference,
    reminders: ReminderSettingsPresenter,
    backup: BackupPresenter,
    restore: RestorePresenter,
    cloudStorage: CloudStoragePresenter,
    sync: SyncPresenter,
    dataLocation: DataLocationPresenter,
    logLocation: LogLocationPresenter,
    updates: UpdatePresenter,
    installer: UpdateInstallPresenter,
    appVersion: String,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        CategoryList(category, onCategoryChange, updateAvailable = updates.showsBadge)
        // Keyed, so every category opens at its top rather than at the scroll position of the previous one.
        key(category) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
            ) {
                Column(
                    modifier = Modifier.widthIn(max = PAGE_MAX_WIDTH),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (category) {
                        SettingsCategory.Appearance -> AppearancePage(themePreference, scalePreference)
                        SettingsCategory.Reminders -> RemindersPage(reminders)
                        SettingsCategory.Backup -> BackupPage(backup, restore, cloudStorage)
                        SettingsCategory.Sync -> SyncPage(sync)
                        SettingsCategory.Files -> FilesPage(dataLocation, logLocation)
                        SettingsCategory.Updates -> UpdatesPage(updates, installer)
                        SettingsCategory.About -> AboutPage(appVersion, updates)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryList(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    updateAvailable: Boolean,
) {
    Column(modifier = Modifier.width(CATEGORY_LIST_WIDTH).padding(start = 24.dp, top = 24.dp, end = 8.dp)) {
        Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        SettingsCategory.entries.forEach { category ->
            NavigationDrawerItem(
                label = { Text(stringResource(category.label)) },
                selected = category == selected,
                onClick = { onSelect(category) },
                // Repeats the mark on the Settings rail item, so the pilot finds where the update is explained.
                badge = {
                    if (category == SettingsCategory.Updates && updateAvailable) {
                        Icon(
                            UpdateIcon,
                            contentDescription = stringResource(Res.string.update_rail_badge),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors =
                    NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        unselectedContainerColor = Color.Transparent,
                    ),
            )
        }
    }
}

/** A titled block of one settings page; the hint says what the setting does before the pilot changes it. */
@Composable
internal fun SettingsGroup(
    title: StringResource,
    hint: StringResource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                hint?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun AppearancePage(
    themePreference: ThemePreference,
    scalePreference: InterfaceScalePreference,
) {
    SettingsGroup(Res.string.settings_theme) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themePreference.mode == mode,
                    onClick = { themePreference.mode = mode },
                    label = { Text(stringResource(mode.label())) },
                )
            }
        }
    }
    SettingsGroup(Res.string.settings_scale_title, Res.string.settings_scale_hint) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = scalePreference.percent == null,
                onClick = { scalePreference.percent = null },
                label = { Text(stringResource(Res.string.scale_automatic)) },
            )
            InterfaceScalePreference.CHOICES.forEach { percent ->
                FilterChip(
                    selected = scalePreference.percent == percent,
                    onClick = { scalePreference.percent = percent },
                    label = { Text("$percent %") },
                )
            }
        }
    }
}

private fun ThemeMode.label() =
    when (this) {
        ThemeMode.System -> Res.string.theme_system
        ThemeMode.Light -> Res.string.theme_light
        ThemeMode.Dark -> Res.string.theme_dark
    }

@Composable
private fun BackupPage(
    backup: BackupPresenter,
    restore: RestorePresenter,
    cloudStorage: CloudStoragePresenter,
) {
    SettingsGroup(Res.string.backup_title, Res.string.backup_hint) { BackupSection(backup, restore) }
    SettingsGroup(Res.string.cloud_title, Res.string.cloud_hint) { CloudStorageSection(cloudStorage) }
}

@Composable
private fun SyncPage(sync: SyncPresenter) {
    SettingsGroup(Res.string.sync_title, Res.string.sync_hint) { SyncSection(sync) }
}

@Composable
private fun UpdatesPage(
    updates: UpdatePresenter,
    installer: UpdateInstallPresenter,
) {
    SettingsGroup(Res.string.update_title) { UpdatesSection(updates, installer) }
}

// The published changelog on the main branch, so it also shows what changed in releases newer than the installed one.
private const val CHANGELOG_URL = "https://github.com/michaelheiniger/wing-tracker/blob/main/CHANGELOG.md"

@Composable
private fun AboutPage(
    appVersion: String,
    updates: UpdatePresenter,
) {
    val uriHandler = LocalUriHandler.current
    SettingsGroup(Res.string.about_title) {
        // Selectable, so the version can be pasted into a bug report.
        Text(stringResource(Res.string.about_version, appVersion), style = MaterialTheme.typography.bodyLarge)
        updates.update?.takeIf { updates.showsBadge }?.let { UpdateBadge(it.latest.version.toString()) }
        OutlinedButton(onClick = { uriHandler.openUri(CHANGELOG_URL) }) {
            Text(stringResource(Res.string.about_changelog))
        }
    }
}
