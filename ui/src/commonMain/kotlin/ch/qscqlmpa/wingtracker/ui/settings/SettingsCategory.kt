package ch.qscqlmpa.wingtracker.ui.settings

import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.settings_category_about
import ch.qscqlmpa.wingtracker.ui.resources.settings_category_appearance
import ch.qscqlmpa.wingtracker.ui.resources.settings_category_backup
import ch.qscqlmpa.wingtracker.ui.resources.settings_category_files
import ch.qscqlmpa.wingtracker.ui.resources.settings_category_reminders
import ch.qscqlmpa.wingtracker.ui.resources.settings_category_updates
import org.jetbrains.compose.resources.StringResource

/** One page of the settings at a time, so each stays short as settings keep being added. */
enum class SettingsCategory(
    val label: StringResource,
) {
    Appearance(Res.string.settings_category_appearance),
    Reminders(Res.string.settings_category_reminders),
    Backup(Res.string.settings_category_backup),
    Files(Res.string.settings_category_files),
    Updates(Res.string.settings_category_updates),
    About(Res.string.settings_category_about),
}
