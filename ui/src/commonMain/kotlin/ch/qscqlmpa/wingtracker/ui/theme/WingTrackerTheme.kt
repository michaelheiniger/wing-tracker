package ch.qscqlmpa.wingtracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import ch.qscqlmpa.wingtracker.settings.ThemeMode

/** Lets canvas-drawn content pick its own dark/light variant, which Material color roles cannot express. */
val LocalDarkTheme = staticCompositionLocalOf { false }

val LocalExtraColors = staticCompositionLocalOf { LightExtraColors }

/** The status colours of the current theme, next to `MaterialTheme.colorScheme`. */
val MaterialTheme.extraColors: ExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtraColors.current

@Composable
fun WingTrackerTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark(isSystemInDarkTheme())
    CompositionLocalProvider(
        LocalDarkTheme provides dark,
        LocalExtraColors provides if (dark) DarkExtraColors else LightExtraColors,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColorScheme else LightColorScheme,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
