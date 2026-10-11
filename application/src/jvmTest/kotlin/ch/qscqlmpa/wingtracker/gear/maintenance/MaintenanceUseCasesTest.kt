package ch.qscqlmpa.wingtracker.gear.maintenance

import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.aFixedClock
import ch.qscqlmpa.wingtracker.gear.wing.aWing
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException

class MaintenanceUseCasesTest {
    private val gear = mockk<MaintainedGearRepository>(relaxUnitFun = true)
    private val attachments = mockk<AttachmentStore>(relaxUnitFun = true)
    private val clock = aFixedClock()
    private val wing = aWing()

    private fun givenTheWing(existing: MaintainedGear = wing) {
        coEvery { gear.find(existing.ref) } returns existing
    }

    @Nested
    inner class RecordCheckUseCase {
        private val recordCheck = RecordCheck(gear, attachments, clock) { CheckId("new-check") }

        @Test
        fun `adds the check to the history and saves the wing`() =
            runTest {
                // given
                givenTheWing()

                // when
                val updated = recordCheck(wing.ref, aCheckDetails())

                // then
                assertThat(updated.history.checks.map { it.id.value }).containsExactly("new-check")
                coVerify(exactly = 1) { gear.save(updated) }
            }

        @Test
        fun `stores each file and keeps its name on the check`() =
            runTest {
                // given
                givenTheWing()

                // when
                val updated = recordCheck(wing.ref, aCheckDetails(), files = listOf(aPdf("a.pdf"), aPdf("b.pdf")))

                // then
                assertThat(
                    updated.history.checks
                        .single()
                        .reports
                        .map { it.fileName },
                ).containsExactly("a.pdf", "b.pdf")
                coVerify(exactly = 2) { attachments.save(any(), any()) }
            }

        @Test
        fun `trims the company and drops blank notes`() =
            runTest {
                // given
                givenTheWing()

                // when
                val updated =
                    recordCheck(wing.ref, aCheckDetails().copy(company = "  Swiss Paragliding Service ", notes = "  "))

                // then
                assertThat(
                    updated.history.checks
                        .single()
                        .company,
                ).isEqualTo("Swiss Paragliding Service")
                assertThat(
                    updated.history.checks
                        .single()
                        .notes,
                ).isNull()
            }

        @Test
        fun `completes a planned check so it becomes history instead of staying planned`() =
            runTest {
                // given
                val planned = aStoredPlannedCheck()
                givenTheWing(aWing(plannedChecks = listOf(planned)))

                // when
                val updated = recordCheck(wing.ref, aCheckDetails(), completes = planned.id)

                // then
                assertThat(updated.history.plannedChecks).isEmpty()
                assertThat(updated.history.checks).hasSize(1)
            }

        @Test
        fun `rejects a check dated in the future`() =
            runTest {
                // given
                givenTheWing()

                // when
                val failure =
                    runCatching {
                        recordCheck(
                            wing.ref,
                            aCheckDetails(date = LocalDate(2026, 10, 12)),
                        )
                    }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(CheckDateInFutureException::class.java)
                coVerify(exactly = 0) { gear.save(any()) }
            }

        @Test
        fun `accepts a check dated today`() =
            runTest {
                // given
                givenTheWing()

                // when
                val updated = recordCheck(wing.ref, aCheckDetails(date = LocalDate(2026, 10, 11)))

                // then
                assertThat(updated.history.checks).hasSize(1)
            }

        @Test
        fun `removes the stored files again when saving the wing fails`() =
            runTest {
                // given
                givenTheWing()
                coEvery { gear.save(any()) } throws IOException("Disk full")

                // when
                val failure =
                    runCatching {
                        recordCheck(
                            wing.ref,
                            aCheckDetails(),
                            files = listOf(aPdf("a.pdf")),
                        )
                    }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(IOException::class.java)
                coVerify(exactly = 1) { attachments.delete(any()) }
            }

        @Test
        fun `fails when the wing does not exist`() =
            runTest {
                // given
                coEvery { gear.find(wing.ref) } returns null

                // when
                val failure = runCatching { recordCheck(wing.ref, aCheckDetails()) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(GearNotFoundException::class.java)
            }
    }

    @Nested
    inner class OtherGear {
        private val harness = aHarnessWithInterval()

        @Test
        fun `records a check on a harness`() =
            runTest {
                // given
                coEvery { gear.find(harness.ref) } returns harness
                val recordCheck = RecordCheck(gear, attachments, clock) { CheckId("new-check") }

                // when
                val updated = recordCheck(harness.ref, aCheckDetails(type = CheckType.Check))

                // then
                assertThat(
                    updated.history.checks
                        .single()
                        .type,
                ).isEqualTo(CheckType.Check)
                coVerify(exactly = 1) { gear.save(updated) }
            }

        @Test
        fun `rejects a check type the gear does not accept and removes the stored files`() =
            runTest {
                // given
                coEvery { gear.find(harness.ref) } returns harness
                val recordCheck = RecordCheck(gear, attachments, clock) { CheckId("new-check") }

                // when
                val failure =
                    runCatching {
                        recordCheck(
                            harness.ref,
                            aCheckDetails(type = CheckType.Full),
                            files = listOf(aPdf()),
                        )
                    }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
                coVerify(exactly = 0) { gear.save(any()) }
                coVerify(exactly = 1) { attachments.delete(any()) }
            }
    }

    @Nested
    inner class UpdateCheckUseCase {
        private val updateCheck = UpdateCheck(gear, attachments, clock)

        @Test
        fun `changes the details of the check`() =
            runTest {
                // given
                val check = aStoredCheck()
                givenTheWing(aWing(checks = listOf(check)))

                // when
                val updated = updateCheck(wing.ref, check.id, aCheckDetails(type = CheckType.Partial))

                // then
                assertThat(updated.history.checkOf(check.id).type).isEqualTo(CheckType.Partial)
                assertThat(updated.history.checkOf(check.id).reports).isEqualTo(check.reports)
            }

        @Test
        fun `adds new files and deletes removed ones only after the wing was saved`() =
            runTest {
                // given
                val check = aStoredCheck()
                givenTheWing(aWing(checks = listOf(check)))

                // when
                val updated =
                    updateCheck(
                        wing.ref,
                        check.id,
                        aCheckDetails(),
                        newFiles = listOf(aPdf("new.pdf")),
                        removedFiles = setOf(AttachmentId("report-1")),
                    )

                // then
                assertThat(
                    updated.history
                        .checkOf(check.id)
                        .reports
                        .map { it.fileName },
                ).containsExactly("new.pdf")
                io.mockk.coVerifyOrder {
                    gear.save(updated)
                    attachments.delete(AttachmentId("report-1"))
                }
            }

        @Test
        fun `keeps the removed files when saving fails`() =
            runTest {
                // given
                val check = aStoredCheck()
                givenTheWing(aWing(checks = listOf(check)))
                coEvery { gear.save(any()) } throws IOException("Disk full")

                // when
                runCatching {
                    updateCheck(
                        wing.ref,
                        check.id,
                        aCheckDetails(),
                        removedFiles = setOf(AttachmentId("report-1")),
                    )
                }

                // then
                coVerify(exactly = 0) { attachments.delete(AttachmentId("report-1")) }
            }

        @Test
        fun `rejects a future date`() =
            runTest {
                // given
                val check = aStoredCheck()
                givenTheWing(aWing(checks = listOf(check)))

                // when
                val failure =
                    runCatching {
                        updateCheck(
                            wing.ref,
                            check.id,
                            aCheckDetails(date = LocalDate(2027, 1, 1)),
                        )
                    }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(CheckDateInFutureException::class.java)
            }
    }

    @Nested
    inner class DeleteCheckUseCase {
        @Test
        fun `removes the check and then its files`() =
            runTest {
                // given
                val check = aStoredCheck()
                givenTheWing(aWing(checks = listOf(check)))

                // when
                val updated = DeleteCheck(gear, attachments)(wing.ref, check.id)

                // then
                assertThat(updated.history.checks).isEmpty()
                io.mockk.coVerifyOrder {
                    gear.save(updated)
                    attachments.delete(AttachmentId("report-1"))
                }
            }
    }

    @Nested
    inner class PlannedChecks {
        @Test
        fun `plans a check for today or later`() =
            runTest {
                // given
                givenTheWing()
                val planCheck = PlanCheck(gear, clock) { PlannedCheckId("new-plan") }

                // when
                val updated = planCheck(wing.ref, PlannedCheckDetails(LocalDate(2026, 10, 11), CheckType.Full))

                // then
                assertThat(updated.history.plannedChecks.map { it.id.value }).containsExactly("new-plan")
            }

        @Test
        fun `rejects planning in the past`() =
            runTest {
                // given
                givenTheWing()
                val planCheck = PlanCheck(gear, clock) { PlannedCheckId("new-plan") }

                // when
                val failure =
                    runCatching { planCheck(wing.ref, PlannedCheckDetails(LocalDate(2026, 10, 10), CheckType.Full)) }
                        .exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(PlannedDateInPastException::class.java)
            }

        @Test
        fun `reschedules a planned check`() =
            runTest {
                // given
                val planned = aStoredPlannedCheck()
                givenTheWing(aWing(plannedChecks = listOf(planned)))

                // when
                val updated =
                    UpdatePlannedCheck(gear, clock)(
                        wing.ref,
                        planned.id,
                        PlannedCheckDetails(LocalDate(2027, 2, 1), CheckType.Full),
                    )

                // then
                assertThat(
                    updated.history.plannedChecks
                        .single()
                        .date,
                ).isEqualTo(LocalDate(2027, 2, 1))
                assertThat(
                    updated.history.plannedChecks
                        .single()
                        .type,
                ).isEqualTo(CheckType.Full)
            }

        @Test
        fun `cancels a planned check`() =
            runTest {
                // given
                val planned = aStoredPlannedCheck()
                givenTheWing(aWing(plannedChecks = listOf(planned)))

                // when
                val updated = CancelPlannedCheck(gear)(wing.ref, planned.id)

                // then
                assertThat(updated.history.plannedChecks).isEmpty()
                coVerify(exactly = 1) { gear.save(updated) }
            }
    }
}
