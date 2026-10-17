package ch.qscqlmpa.wingtracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * The palette comes from the application icon: a sky blue as the main colour, the canopy's orange as the single
 * accent. Neutrals are tinted slightly towards the blue so surfaces feel part of the same family.
 * Every text and container pair is checked for WCAG AA contrast in ColorContrastTest (ADR 0019).
 */

private const val OPAQUE = 0xFF000000

private fun hex(value: Long) = Color(OPAQUE or value)

internal val LightColorScheme: ColorScheme =
    lightColorScheme(
        primary = hex(0x0061A4),
        onPrimary = hex(0xFFFFFF),
        primaryContainer = hex(0xD1E4FF),
        onPrimaryContainer = hex(0x001D36),
        inversePrimary = hex(0x9ECAFF),
        secondary = hex(0x535F70),
        onSecondary = hex(0xFFFFFF),
        secondaryContainer = hex(0xD7E3F8),
        onSecondaryContainer = hex(0x101C2B),
        tertiary = hex(0x944700),
        onTertiary = hex(0xFFFFFF),
        tertiaryContainer = hex(0xFFDCC5),
        onTertiaryContainer = hex(0x311300),
        background = hex(0xFDFCFF),
        onBackground = hex(0x1A1C1E),
        surface = hex(0xFDFCFF),
        onSurface = hex(0x1A1C1E),
        surfaceVariant = hex(0xDFE2EB),
        onSurfaceVariant = hex(0x42474E),
        surfaceTint = hex(0x0061A4),
        inverseSurface = hex(0x2F3033),
        inverseOnSurface = hex(0xF1F0F4),
        error = hex(0xBA1A1A),
        onError = hex(0xFFFFFF),
        errorContainer = hex(0xFFDAD6),
        onErrorContainer = hex(0x410002),
        outline = hex(0x73777F),
        outlineVariant = hex(0xC3C7CF),
        scrim = hex(0x000000),
        surfaceBright = hex(0xFDFCFF),
        surfaceDim = hex(0xDADADE),
        surfaceContainerLowest = hex(0xFFFFFF),
        surfaceContainerLow = hex(0xF3F3F7),
        surfaceContainer = hex(0xEDEDF1),
        surfaceContainerHigh = hex(0xE8E8EC),
        surfaceContainerHighest = hex(0xE2E2E6),
    )

internal val DarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = hex(0x9ECAFF),
        onPrimary = hex(0x003258),
        primaryContainer = hex(0x00497D),
        onPrimaryContainer = hex(0xD1E4FF),
        inversePrimary = hex(0x0061A4),
        secondary = hex(0xBBC7DB),
        onSecondary = hex(0x253140),
        secondaryContainer = hex(0x3B4858),
        onSecondaryContainer = hex(0xD7E3F8),
        tertiary = hex(0xFFB784),
        onTertiary = hex(0x502400),
        tertiaryContainer = hex(0x733500),
        onTertiaryContainer = hex(0xFFDCC5),
        background = hex(0x1A1C1E),
        onBackground = hex(0xE3E2E6),
        surface = hex(0x1A1C1E),
        onSurface = hex(0xE3E2E6),
        surfaceVariant = hex(0x42474E),
        onSurfaceVariant = hex(0xC3C7CF),
        surfaceTint = hex(0x9ECAFF),
        inverseSurface = hex(0xE3E2E6),
        inverseOnSurface = hex(0x2F3033),
        error = hex(0xFFB4AB),
        onError = hex(0x690005),
        errorContainer = hex(0x93000A),
        onErrorContainer = hex(0xFFDAD6),
        outline = hex(0x8D9199),
        outlineVariant = hex(0x42474E),
        scrim = hex(0x000000),
        surfaceBright = hex(0x38393C),
        surfaceDim = hex(0x121316),
        surfaceContainerLowest = hex(0x0F1114),
        surfaceContainerLow = hex(0x1D2024),
        surfaceContainer = hex(0x1E2022),
        surfaceContainerHigh = hex(0x282A2D),
        surfaceContainerHighest = hex(0x333538),
    )

/**
 * What Material has no role for: the brand accent (the canopy orange, used sparingly for primary actions) and the
 * meanings "good" and "warning". Critical is the scheme's error. The text colours sit on the normal surface; the
 * container pairs are for filled badges and panels.
 */
@Immutable
class ExtraColors(
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    // The map's tiles look the same in light and dark, so its colours do too (ADR 0027).
    val map: MapColors = MapPalette,
)

/** The flown part of a track, the recording before and after it, the outline that lifts both off any terrain. */
@Immutable
class MapColors(
    val track: Color,
    val trackGround: Color,
    val trackHalo: Color,
    // Shown where no tile could be had, e.g. offline.
    val background: Color,
    // The climb/sink colouring, from sinking to strong climb.
    val sink: Color,
    val weakClimb: Color,
    val moderateClimb: Color,
    val strongClimb: Color,
)

internal val MapPalette =
    MapColors(
        track = hex(0xD81B60),
        trackGround = hex(0x5F6368),
        trackHalo = hex(0xFFFFFF),
        background = hex(0xE8E4DA),
        sink = hex(0x1565C0),
        weakClimb = hex(0x2E7D32),
        moderateClimb = hex(0xEF6C00),
        strongClimb = hex(0xC62828),
    )

internal val LightExtraColors =
    ExtraColors(
        accent = hex(0xF57C1F),
        onAccent = hex(0x1F0C00),
        success = hex(0x1B6E2B),
        successContainer = hex(0xC8F0CE),
        onSuccessContainer = hex(0x00210B),
        warning = hex(0x7A5900),
        warningContainer = hex(0xFFE9A8),
        onWarningContainer = hex(0x3B2A00),
    )

internal val DarkExtraColors =
    ExtraColors(
        accent = hex(0xFFB784),
        onAccent = hex(0x502400),
        success = hex(0x7FD98B),
        successContainer = hex(0x0A4D1A),
        onSuccessContainer = hex(0xB6F2BE),
        warning = hex(0xF2C14E),
        warningContainer = hex(0x5C4300),
        onWarningContainer = hex(0xFFE29A),
    )
