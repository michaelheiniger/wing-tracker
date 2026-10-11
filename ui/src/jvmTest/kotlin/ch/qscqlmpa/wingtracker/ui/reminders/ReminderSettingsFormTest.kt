package ch.qscqlmpa.wingtracker.ui.reminders

import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ReminderSettingsFormTest {
    @Nested
    inner class Validate {
        @Test
        fun `reads days and hours`() {
            // given
            val input = ReminderSettingsInput(days = "21", hours = "12:30")

            // when
            val result = input.validate()

            // then
            assertThat((result as ReminderSettingsResult.Valid).leadTimes)
                .isEqualTo(ReminderLeadTimes(days = 21, hours = 12.hours + 30.minutes))
        }

        @Test
        fun `rejects zero days`() {
            // given
            val input = ReminderSettingsInput(days = "0", hours = "10")

            // when
            val errors = (input.validate() as ReminderSettingsResult.Invalid).errors

            // then
            assertThat(
                errors,
            ).containsOnly(
                org.assertj.core.api.Assertions
                    .entry(ReminderSettingsField.Days, FormError.InvalidDays),
            )
        }

        @Test
        fun `rejects days that are not a whole number`() {
            // given
            val input = ReminderSettingsInput(days = "2.5", hours = "10")

            // when
            val errors = (input.validate() as ReminderSettingsResult.Invalid).errors

            // then
            assertThat(errors).containsKey(ReminderSettingsField.Days)
        }

        @Test
        fun `rejects hours that are zero or not a duration`() {
            // given
            val zero = ReminderSettingsInput(days = "30", hours = "0")
            val text = ReminderSettingsInput(days = "30", hours = "ten")

            // when / then
            assertThat((zero.validate() as ReminderSettingsResult.Invalid).errors)
                .containsEntry(ReminderSettingsField.Hours, FormError.InvalidHours)
            assertThat((text.validate() as ReminderSettingsResult.Invalid).errors)
                .containsEntry(ReminderSettingsField.Hours, FormError.InvalidHours)
        }

        @Test
        fun `reports both fields at once`() {
            // given
            val input = ReminderSettingsInput()

            // when
            val errors = (input.validate() as ReminderSettingsResult.Invalid).errors

            // then
            assertThat(errors.keys).containsExactly(ReminderSettingsField.Days, ReminderSettingsField.Hours)
        }
    }

    @Nested
    inner class From {
        @Test
        fun `round-trips lead times through the form`() {
            // given
            val leadTimes = ReminderLeadTimes(days = 45, hours = 7.hours + 15.minutes)

            // when
            val result = ReminderSettingsInput.from(leadTimes).validate()

            // then
            assertThat((result as ReminderSettingsResult.Valid).leadTimes).isEqualTo(leadTimes)
        }
    }
}
