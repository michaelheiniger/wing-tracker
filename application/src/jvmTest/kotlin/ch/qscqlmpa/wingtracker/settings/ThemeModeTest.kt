package ch.qscqlmpa.wingtracker.settings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ThemeModeTest {
    @Nested
    inner class IsDark {
        @Test
        fun `System follows the operating system when it is dark`() {
            // given
            val mode = ThemeMode.System

            // when
            val dark = mode.isDark(systemInDarkTheme = true)

            // then
            assertThat(dark).isTrue()
        }

        @Test
        fun `System follows the operating system when it is light`() {
            // given
            val mode = ThemeMode.System

            // when
            val dark = mode.isDark(systemInDarkTheme = false)

            // then
            assertThat(dark).isFalse()
        }

        @Test
        fun `Light stays light even when the operating system is dark`() {
            // given
            val mode = ThemeMode.Light

            // when
            val dark = mode.isDark(systemInDarkTheme = true)

            // then
            assertThat(dark).isFalse()
        }

        @Test
        fun `Dark stays dark even when the operating system is light`() {
            // given
            val mode = ThemeMode.Dark

            // when
            val dark = mode.isDark(systemInDarkTheme = false)

            // then
            assertThat(dark).isTrue()
        }
    }
}
