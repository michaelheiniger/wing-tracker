package ch.qscqlmpa.wingtracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import ch.qscqlmpa.wingtracker.settings.ThemeMode

/** Lets canvas-drawn content pick its own dark/light variant, which Material color roles cannot express. */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun WingTrackerTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark(isSystemInDarkTheme())
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
            content = content,
        )
    }
}
