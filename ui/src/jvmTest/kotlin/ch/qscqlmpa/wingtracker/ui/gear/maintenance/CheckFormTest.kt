package ch.qscqlmpa.wingtracker.ui.gear.maintenance

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckDetails
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheckDetails
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheckId
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// Own factories: UI tests do not share fixtures with the other modules' tests.
private val REPORT = Attachment(AttachmentId("report-1"), "check-report-2025.pdf")

private fun aCheckInput() =
    CheckFormInput(
        date = LocalDate(2026, 4, 10),
        type = CheckType.Full,
        company = "Swiss Paragliding Service",
        notes = "Lines within tolerance",
    )

private fun aStoredCheck() =
    MaintenanceCheck(
        id = CheckId("check-1"),
        date = LocalDate(2025, 4, 10),
        type = CheckType.Full,
        company = "Swiss Paragliding Service",
        notes = "Lines within tolerance",
        reports = listOf(REPORT),
    )

private fun aPlanned() =
    PlannedCheck(
        PlannedCheckId("plan-1"),
        LocalDate(2026, 12, 1),
        CheckType.Partial,
        "Swiss Paragliding Service",
        "Trim",
    )

class CheckFormTest {
    @Nested
    inner class ValidateCheck {
        @Test
        fun `builds the check details from a filled form`() {
            // given
            val input = aCheckInput()

            // when
            val result = input.validate()

            // then
            val expected =
                CheckDetails(
                    date = LocalDate(2026, 4, 10),
                    type = CheckType.Full,
                    company = "Swiss Paragliding Service",
                    notes = "Lines within tolerance",
                    restartsInterval = true,
                )
            assertThat((result as CheckFormResult.Valid).details).usingRecursiveComparison().isEqualTo(expected)
        }

        @Test
        fun `turns blank company and notes into absent values`() {
            // given
            val input = aCheckInput().copy(company = " ", notes = "")

            // when
            val details = (input.validate() as CheckFormResult.Valid).details

            // then
            assertThat(details.company).isNull()
            assertThat(details.notes).isNull()
        }

        @Test
        fun `requires a date`() {
            // given
            val input = aCheckInput().copy(date = null)

            // when
            val result = input.validate()

            // then
            assertThat((result as CheckFormResult.Invalid).errors)
                .containsEntry(CheckFormField.Date, CheckFormError.Required)
        }

        @Test
        fun `passes the files to add and works out which stored files were removed`() {
            // given
            val added = NewAttachment("new.pdf", byteArrayOf(1))
            val input =
                CheckFormInput
                    .from(aStoredCheck())
                    .copy(keptReports = emptyList(), newReports = listOf(added))

            // when
            val valid = input.validate() as CheckFormResult.Valid

            // then
            assertThat(valid.newFiles).containsExactly(added)
            assertThat(valid.removedFiles).containsExactly(REPORT.id)
        }

        @Test
        fun `removes nothing when every stored file is kept`() {
            // given
            val input = CheckFormInput.from(aStoredCheck())

            // when
            val valid = input.validate() as CheckFormResult.Valid

            // then
            assertThat(valid.removedFiles).isEmpty()
        }
    }

    @Nested
    inner class IntervalRestart {
        @Test
        fun `switching to other keeps the checkbox choice`() {
            // given
            val input = aCheckInput().withType(CheckType.Other).copy(restartsInterval = false)

            // when
            val details = (input.validate() as CheckFormResult.Valid).details

            // then
            assertThat(details.restartsInterval).isFalse()
        }

        @Test
        fun `switching from other to a partial check forces the interval to restart`() {
            // given
            val input = aCheckInput().withType(CheckType.Other).copy(restartsInterval = false)

            // when
            val partial = input.withType(CheckType.Partial)

            // then
            assertThat(partial.restartsInterval).isTrue()
        }
    }

    @Nested
    inner class Completing {
        @Test
        fun `starts from the planned check with today as the date`() {
            // given
            val planned = aPlanned()

            // when
            val input = CheckFormInput.completing(planned, today = LocalDate(2026, 12, 3))

            // then
            assertThat(input.date).isEqualTo(LocalDate(2026, 12, 3))
            assertThat(input.type).isEqualTo(CheckType.Partial)
            assertThat(input.company).isEqualTo("Swiss Paragliding Service")
            assertThat(input.notes).isEqualTo("Trim")
        }
    }

    @Nested
    inner class ValidatePlan {
        @Test
        fun `builds the planned check details`() {
            // given
            val input = PlanFormInput(LocalDate(2026, 12, 1), CheckType.Partial, "Swiss Paragliding Service", "")

            // when
            val result = input.validate()

            // then
            val expected =
                PlannedCheckDetails(LocalDate(2026, 12, 1), CheckType.Partial, "Swiss Paragliding Service", null)
            assertThat((result as PlanFormResult.Valid).details).usingRecursiveComparison().isEqualTo(expected)
        }

        @Test
        fun `requires a date`() {
            // given
            val input = PlanFormInput()

            // when
            val result = input.validate()

            // then
            assertThat((result as PlanFormResult.Invalid).errors)
                .containsEntry(CheckFormField.Date, PlanFormError.Required)
        }
    }
}
