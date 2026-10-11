package ch.qscqlmpa.wingtracker.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.settings.ThemeMode

/** Observable holder so any screen can change the theme without knowing who renders it. */
class ThemePreference(
    initial: ThemeMode = ThemeMode.System,
) {
    var mode: ThemeMode by mutableStateOf(initial)
}
