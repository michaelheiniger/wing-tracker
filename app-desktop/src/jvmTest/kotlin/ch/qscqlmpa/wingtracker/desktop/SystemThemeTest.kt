package ch.qscqlmpa.wingtracker.desktop

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SystemThemeTest {
    private fun theme(answers: Map<String, String>) = SystemTheme { command -> answers[command.first()] }

    @Nested
    inner class Parsing {
        @Test
        fun `portal value 1 is dark and 2 is light`() {
            assertThat(SystemTheme.parsePortal("v v u 1\n")).isTrue()
            assertThat(SystemTheme.parsePortal("v v u 2\n")).isFalse()
        }

        @Test
        fun `portal value 0 means no preference`() {
            assertThat(SystemTheme.parsePortal("v v u 0")).isNull()
        }

        @Test
        fun `gsettings quotes are ignored and default is no opinion`() {
            assertThat(SystemTheme.parseGsettings("'prefer-dark'\n")).isTrue()
            assertThat(SystemTheme.parseGsettings("'prefer-light'")).isFalse()
            assertThat(SystemTheme.parseGsettings("'default'")).isNull()
        }

        @Test
        fun `a KDE colour scheme with Dark in its name is dark`() {
            assertThat(SystemTheme.parseKdeColorScheme("BreezeDark\n")).isTrue()
            assertThat(SystemTheme.parseKdeColorScheme("BreezeLight")).isFalse()
            assertThat(SystemTheme.parseKdeColorScheme("")).isNull()
        }
    }

    @Nested
    inner class Sources {
        @Test
        fun `the portal wins over the other sources`() {
            assertThat(theme(mapOf("busctl" to "v v u 1", "gsettings" to "'prefer-light'")).isDark()).isTrue()
        }

        @Test
        fun `falls back to the next source when the portal has no answer`() {
            assertThat(theme(mapOf("gsettings" to "'prefer-dark'")).isDark()).isTrue()
            assertThat(theme(mapOf("kreadconfig6" to "BreezeDark")).isDark()).isTrue()
        }

        @Test
        fun `is unknown when nothing answers`() {
            assertThat(theme(emptyMap()).isDark()).isNull()
        }
    }
}
