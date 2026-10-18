package ch.qscqlmpa.wingtracker.flights

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private fun aSiteNames() =
    SiteNames(
        mapOf(
            SiteSource.Manual to "Niederhorn West",
            SiteSource.Syride to "Niederhorn",
            SiteSource.Spreadsheet to "Niederhorn (BE)",
        ),
    )

class SiteNamesTest {
    @Nested
    inner class Creation {
        @Test
        fun `rejects a blank name`() {
            assertThatThrownBy { SiteNames(mapOf(SiteSource.Syride to " ")) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class Shown {
        @Test
        fun `prefers the name the pilot typed`() {
            // given
            val names = aSiteNames()

            // when
            val shown = names.shown

            // then
            assertThat(shown).isEqualTo("Niederhorn West")
            assertThat(names.shownSource).isEqualTo(SiteSource.Manual)
        }

        @Test
        fun `prefers Syride over a spreadsheet when the pilot typed nothing`() {
            // given
            val names = aSiteNames().with(SiteSource.Manual, null)

            // when
            val shown = names.shown

            // then
            assertThat(shown).isEqualTo("Niederhorn")
            assertThat(names.shownSource).isEqualTo(SiteSource.Syride)
        }

        @Test
        fun `falls back to the spreadsheet when nothing else has a name`() {
            // given
            val names = SiteNames.of(SiteSource.Spreadsheet, "Niederhorn (BE)")

            // when
            val shown = names.shown

            // then
            assertThat(shown).isEqualTo("Niederhorn (BE)")
        }

        @Test
        fun `is absent when no source has a name`() {
            // given
            val names = SiteNames.NONE

            // when
            val shown = names.shown

            // then
            assertThat(shown).isNull()
            assertThat(names.shownSource).isNull()
        }
    }

    @Nested
    inner class With {
        @Test
        fun `sets the name of one source and keeps the others`() {
            // given
            val names = SiteNames.of(SiteSource.Syride, "Niederhorn")

            // when
            val changed = names.with(SiteSource.Manual, "Niederhorn West")

            // then
            assertThat(changed.bySource).containsExactlyInAnyOrderEntriesOf(
                mapOf(SiteSource.Syride to "Niederhorn", SiteSource.Manual to "Niederhorn West"),
            )
        }

        @Test
        fun `removes the name of a source when it is blank`() {
            // given
            val names = aSiteNames()

            // when
            val changed = names.with(SiteSource.Manual, "  ")

            // then
            assertThat(changed[SiteSource.Manual]).isNull()
            assertThat(changed[SiteSource.Syride]).isEqualTo("Niederhorn")
        }

        @Test
        fun `trims the name`() {
            // given
            val names = SiteNames.NONE

            // when
            val changed = names.with(SiteSource.Manual, " Niederhorn ")

            // then
            assertThat(changed[SiteSource.Manual]).isEqualTo("Niederhorn")
        }
    }
}
