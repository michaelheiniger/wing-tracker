package ch.qscqlmpa.wingtracker.ui.sync

import ch.qscqlmpa.wingtracker.sync.SyncSettings
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private val TYPED =
    SyncInput(
        repository = " https://codeberg.org/michael/logbook.git ",
        username = " ",
        token = " ghp_exampletoken ",
        authorName = "Michael Heiniger ",
        authorEmail = " michael@example.org",
    )

class SyncFormTest {
    @Nested
    inner class Validate {
        @Test
        fun `trims what was typed and leaves out an empty username`() {
            // given / when
            val result = TYPED.validate(tokenStored = false)

            // then
            assertThat(result).isEqualTo(
                SyncFormResult.Valid(
                    SyncSettings(
                        "https://codeberg.org/michael/logbook.git",
                        null,
                        "Michael Heiniger",
                        "michael@example.org",
                    ),
                    "ghp_exampletoken",
                ),
            )
        }

        @Test
        fun `asks for an HTTPS repository and an email address`() {
            // given
            val input = TYPED.copy(repository = "git@codeberg.org:michael/logbook.git", authorEmail = "michael")

            // when
            val result = input.validate(tokenStored = false)

            // then
            assertThat((result as SyncFormResult.Invalid).errors).isEqualTo(
                mapOf(
                    SyncField.Repository to FormError.InvalidEndpoint,
                    SyncField.AuthorEmail to FormError.InvalidEmail,
                ),
            )
        }

        @Test
        fun `lets the token stay empty when one is kept`() {
            // given
            val input = TYPED.copy(token = "")

            // when
            val result = input.validate(tokenStored = true)

            // then
            assertThat((result as SyncFormResult.Valid).token).isNull()
        }

        @Test
        fun `asks for a token when none is kept`() {
            // given
            val input = TYPED.copy(token = "")

            // when
            val result = input.validate(tokenStored = false)

            // then
            assertThat((result as SyncFormResult.Invalid).errors).containsOnlyKeys(SyncField.Token)
        }
    }
}
