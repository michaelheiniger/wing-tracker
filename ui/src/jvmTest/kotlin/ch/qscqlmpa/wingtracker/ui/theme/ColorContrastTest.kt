package ch.qscqlmpa.wingtracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import ch.qscqlmpa.wingtracker.ui.statistics.ChartTheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.pow

private const val TEXT_MINIMUM = 4.5
private const val COMPONENT_MINIMUM = 3.0

/** WCAG 2.x contrast ratio between two opaque colours. */
private fun contrast(
    first: Color,
    second: Color,
): Double {
    val a = luminance(first)
    val b = luminance(second)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

private fun luminance(color: Color): Double {
    fun channel(value: Float): Double = if (value <= 0.04045f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

private class Pair(
    val name: String,
    val foreground: Color,
    val background: Color,
)

private fun textPairs(
    scheme: ColorScheme,
    status: ExtraColors,
): List<Pair> {
    val surfaces =
        mapOf(
            "surface" to scheme.surface,
            "surfaceContainerLowest" to scheme.surfaceContainerLowest,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            "surfaceContainerHighest" to scheme.surfaceContainerHighest,
        )
    val onSurfaces =
        surfaces.flatMap { (name, background) ->
            listOf(
                Pair("onSurface on $name", scheme.onSurface, background),
                Pair("onSurfaceVariant on $name", scheme.onSurfaceVariant, background),
                // Accent colours are also used as text (links, headings, status lines) on ordinary surfaces.
                Pair("primary on $name", scheme.primary, background),
                Pair("tertiary on $name", scheme.tertiary, background),
                Pair("error on $name", scheme.error, background),
                Pair("success on $name", status.success, background),
                Pair("warning on $name", status.warning, background),
            )
        }
    return onSurfaces +
        listOf(
            Pair("onPrimary on primary", scheme.onPrimary, scheme.primary),
            Pair("onPrimaryContainer on primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer),
            Pair("onSecondaryContainer on secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer),
            Pair("onTertiary on tertiary", scheme.onTertiary, scheme.tertiary),
            Pair("onTertiaryContainer on tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer),
            Pair("onError on error", scheme.onError, scheme.error),
            Pair("onErrorContainer on errorContainer", scheme.onErrorContainer, scheme.errorContainer),
            Pair("onAccent on accent", status.onAccent, status.accent),
            Pair("onSuccessContainer on successContainer", status.onSuccessContainer, status.successContainer),
            Pair("onWarningContainer on warningContainer", status.onWarningContainer, status.warningContainer),
            Pair("inverseOnSurface on inverseSurface", scheme.inverseOnSurface, scheme.inverseSurface),
        )
}

class ColorContrastTest {
    private val modes =
        mapOf(
            "light" to (LightColorScheme to LightExtraColors),
            "dark" to (DarkColorScheme to DarkExtraColors),
        )

    @Nested
    inner class Text {
        @Test
        fun `every text and container pair is readable in both modes (WCAG AA)`() {
            // given
            val tooLow =
                modes.flatMap { (mode, schemes) ->
                    textPairs(schemes.first, schemes.second)
                        .map { Triple(mode, it.name, contrast(it.foreground, it.background)) }
                        .filter { it.third < TEXT_MINIMUM }
                }

            // then
            assertThat(tooLow.map { "${it.first}: ${it.second} = ${"%.2f".format(it.third)}" }).isEmpty()
        }
    }

    @Nested
    inner class Components {
        @Test
        fun `outlines and borders stand out from the surface in both modes`() {
            // given
            val tooLow =
                modes.mapNotNull { (mode, schemes) ->
                    val ratio = contrast(schemes.first.outline, schemes.first.surface)
                    "$mode: outline = ${"%.2f".format(ratio)}".takeIf { ratio < COMPONENT_MINIMUM }
                }

            // then
            assertThat(tooLow).isEmpty()
        }

        @Test
        fun `the primary and accent colours are clearly different from each other`() {
            // given / when / then
            modes.forEach { (mode, schemes) ->
                val ratio = contrast(schemes.first.primary, schemes.first.tertiary)
                assertThat(
                    schemes.first.primary,
                ).describedAs("$mode primary vs tertiary").isNotEqualTo(schemes.first.tertiary)
                assertThat(ratio).describedAs("$mode primary vs tertiary contrast").isGreaterThan(1.0)
            }
        }
    }

    @Nested
    inner class Map {
        @Test
        fun `the track stands out from its outline and from the map's plain background`() {
            // given
            val map = MapPalette
            val pairs =
                listOf(
                    Pair("track on halo", map.track, map.trackHalo),
                    Pair("ground on halo", map.trackGround, map.trackHalo),
                    Pair("track on background", map.track, map.background),
                    Pair("ground on background", map.trackGround, map.background),
                    Pair("strong sink on halo", map.strongSink, map.trackHalo),
                    Pair("weak sink on halo", map.weakSink, map.trackHalo),
                    Pair("weak climb on halo", map.weakClimb, map.trackHalo),
                    Pair("moderate climb on halo", map.moderateClimb, map.trackHalo),
                    Pair("strong climb on halo", map.strongClimb, map.trackHalo),
                )

            // when
            val tooLow =
                pairs.mapNotNull { pair ->
                    val ratio = contrast(pair.foreground, pair.background)
                    "${pair.name} = ${"%.2f".format(ratio)}".takeIf { ratio < COMPONENT_MINIMUM }
                }

            // then
            assertThat(tooLow).isEmpty()
        }

        @Test
        fun `the track colour also stands out in the altitude profile in both modes`() {
            // given
            val surfaces = mapOf("light" to ChartTheme.Light.surface, "dark" to ChartTheme.Dark.surface)

            // when
            val tooLow =
                surfaces.mapNotNull { (mode, surface) ->
                    val ratio = contrast(MapPalette.track, surface)
                    "$mode: track = ${"%.2f".format(ratio)}".takeIf { ratio < COMPONENT_MINIMUM }
                } +
                    listOf("light" to ChartTheme.Light, "dark" to ChartTheme.Dark).mapNotNull { (mode, theme) ->
                        val ratio = contrast(theme.ground, theme.surface)
                        "$mode: ground = ${"%.2f".format(ratio)}".takeIf { ratio < COMPONENT_MINIMUM }
                    }

            // then
            assertThat(tooLow).isEmpty()
        }
    }

    @Nested
    inner class Meaning {
        @Test
        fun `the dark scheme is not just the light scheme`() {
            assertThat(DarkColorScheme.surface).isNotEqualTo(LightColorScheme.surface)
            assertThat(DarkColorScheme.primary).isNotEqualTo(LightColorScheme.primary)
            assertThat(DarkExtraColors.warning).isNotEqualTo(LightExtraColors.warning)
        }
    }
}
