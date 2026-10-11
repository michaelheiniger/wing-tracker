package ch.qscqlmpa.wingtracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.settings.ThemeMode
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.settings_theme
import ch.qscqlmpa.wingtracker.ui.resources.settings_title
import ch.qscqlmpa.wingtracker.ui.resources.theme_dark
import ch.qscqlmpa.wingtracker.ui.resources.theme_light
import ch.qscqlmpa.wingtracker.ui.resources.theme_system
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(themePreference: ThemePreference) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(Res.string.settings_theme), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themePreference.mode == mode,
                    onClick = { themePreference.mode = mode },
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.System -> Res.string.theme_system
                                    ThemeMode.Light -> Res.string.theme_light
                                    ThemeMode.Dark -> Res.string.theme_dark
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}
