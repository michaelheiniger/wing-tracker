package ch.qscqlmpa.wingtracker.ui.theme

import ch.qscqlmpa.wingtracker.settings.ThemeMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ThemePreferenceTest {
    @Nested
    inner class Mode {
        @Test
        fun `defaults to following the operating system`() {
            // given / when
            val preference = ThemePreference()

            // then
            assertThat(preference.mode).isEqualTo(ThemeMode.System)
        }

        @Test
        fun `reflects the mode chosen by the user`() {
            // given
            val preference = ThemePreference()

            // when
            preference.mode = ThemeMode.Dark

            // then
            assertThat(preference.mode).isEqualTo(ThemeMode.Dark)
        }
    }
}
