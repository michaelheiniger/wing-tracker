package ch.qscqlmpa.wingtracker.ui.gear.maintenance

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.maintenance.CancelPlannedCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.DeleteCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.GetGearMaintenance
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGearRepository
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.gear.maintenance.PlanCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheckId
import ch.qscqlmpa.wingtracker.gear.maintenance.ReadAttachment
import ch.qscqlmpa.wingtracker.gear.maintenance.RecordCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.UpdateCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.UpdatePlannedCheck
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.settings.LogbookSettings
import ch.qscqlmpa.wingtracker.settings.LogbookSettingsRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.FileOpener
import ch.qscqlmpa.wingtracker.ui.platform.FilePicker
import ch.qscqlmpa.wingtracker.ui.platform.UnsupportedPathException
import ch.qscqlmpa.wingtracker.ui.undo.TEXT
import ch.qscqlmpa.wingtracker.ui.undo.UndoCoordinator
import ch.qscqlmpa.wingtracker.ui.undo.expire
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

private val WING_ID = GearRef(GearKind.Wing, "wing-1")

private fun aWing(
    checks: List<MaintenanceCheck> = emptyList(),
    planned: List<PlannedCheck> = emptyList(),
) = Wing(
    id =
        ch.qscqlmpa.wingtracker.gear.wing
            .WingId("wing-1"),
    details =
        WingDetails(
            name = "Sky Dancer",
            manufacturer = "Advance",
            model = "Iota 3",
            acquisition = Acquisition(AcquisitionCondition.New),
            inServiceDate = LocalDate(2024, 3, 9),
        ),
    checks = checks,
    plannedChecks = planned,
)

private fun aCheck() = MaintenanceCheck(CheckId("check-1"), LocalDate(2025, 4, 10), CheckType.Full)

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenancePresentersTest {
    private val wings = mockk<MaintainedGearRepository>(relaxUnitFun = true)
    private val flights = mockk<FlightRepository>()
    private val settings = mockk<LogbookSettingsRepository>().also { coEvery { it.load() } returns LogbookSettings() }
    private val attachments = mockk<AttachmentStore>(relaxUnitFun = true)
    private val opener = mockk<FileOpener>(relaxUnitFun = true)
    private val picker = mockk<FilePicker>()
    private val clock =
        AppClock(
            clock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("2026-10-11T18:30:00Z")
                },
            zone = TimeZone.UTC,
        )

    private fun TestScope.useCases() =
        MaintenanceUseCases(
            getGearMaintenance = GetGearMaintenance(wings, flights, settings, clock),
            recordCheck = RecordCheck(wings, attachments, clock) { CheckId("new-check") },
            updateCheck = UpdateCheck(wings, attachments, clock),
            deleteCheck = DeleteCheck(wings, attachments),
            planCheck = PlanCheck(wings, clock) { PlannedCheckId("new-plan") },
            updatePlannedCheck = UpdatePlannedCheck(wings, clock),
            cancelPlannedCheck = CancelPlannedCheck(wings),
            readAttachment = ReadAttachment(attachments),
        )

    private fun TestScope.scope() = TestScope(UnconfinedTestDispatcher(testScheduler))

    private fun givenStored(wing: Wing = aWing()) {
        coEvery { wings.find(WING_ID) } returns wing
        coEvery { wings.findAll() } returns listOf(wing)
        coEvery { flights.findAll() } returns LoadResult(emptyList())
    }

    @Nested
    inner class Overview {
        @Test
        fun `loads the maintenance of the wing`() =
            runTest {
                // given
                givenStored(aWing(checks = listOf(aCheck())))
                val presenter = GearMaintenancePresenter(scope(), WING_ID, useCases(), opener)

                // when
                presenter.refresh()

                // then
                assertThat(
                    presenter.state.data
                        ?.gear
                        ?.history
                        ?.checks,
                ).hasSize(1)
                assertThat(presenter.state.loading).isFalse()
            }

        @Test
        fun `shows the failure when loading fails`() =
            runTest {
                // given
                coEvery { wings.find(WING_ID) } throws IOException("disk unreadable")
                val presenter = GearMaintenancePresenter(scope(), WING_ID, useCases(), opener)

                // when
                presenter.refresh()

                // then
                assertThat(presenter.state.failure).isEqualTo(Failure(FailureKind.LoadMaintenance, "disk unreadable"))
            }

        @Test
        fun `deletes a check and reloads`() =
            runTest {
                // given
                givenStored(aWing(checks = listOf(aCheck())))
                val presenter = GearMaintenancePresenter(scope(), WING_ID, useCases(), opener)

                // when
                val undo = UndoCoordinator()
                presenter.deleteCheck(CheckId("check-1"), undo, TEXT)
                undo.expire()

                // then
                coVerify(exactly = 1) { wings.save(match { it.history.checks.isEmpty() }) }
            }

        @Test
        fun `opens a report with the stored bytes`() =
            runTest {
                // given
                val check =
                    aCheck().copy(
                        reports =
                            listOf(
                                ch.qscqlmpa.wingtracker.attachments.Attachment(
                                    ch.qscqlmpa.wingtracker.attachments
                                        .AttachmentId("report-1"),
                                    "report.pdf",
                                ),
                            ),
                    )
                givenStored(aWing(checks = listOf(check)))
                coEvery { attachments.read(check.reports.single().id) } returns byteArrayOf(1, 2)
                val presenter = GearMaintenancePresenter(scope(), WING_ID, useCases(), opener)

                // when
                presenter.openReport(check.reports.single())

                // then
                coVerify(exactly = 1) { opener.open("report.pdf", byteArrayOf(1, 2)) }
            }

        @Test
        fun `shows a failure when the report cannot be opened`() =
            runTest {
                // given
                givenStored()
                val report =
                    ch.qscqlmpa.wingtracker.attachments.Attachment(
                        ch.qscqlmpa.wingtracker.attachments
                            .AttachmentId("report-1"),
                        "report.pdf",
                    )
                coEvery { attachments.read(report.id) } throws IOException("file is gone")
                val presenter = GearMaintenancePresenter(scope(), WING_ID, useCases(), opener)

                // when
                presenter.openReport(report)

                // then
                assertThat(presenter.state.failure).isEqualTo(Failure(FailureKind.OpenReport, "file is gone"))
            }
    }

    @Nested
    inner class CheckDialogBehaviour {
        private fun TestScope.presenter(
            mode: CheckFormMode,
            input: CheckFormInput,
        ) = CheckFormPresenter(scope(), useCases(), WING_ID, mode, input, picker, "Choose")

        @Test
        fun `records a check with the picked files`() =
            runTest {
                // given
                givenStored()
                coEvery { picker.pick("Choose", setOf("pdf")) } returns listOf(NewAttachment("a.pdf", byteArrayOf(1)))
                val presenter = presenter(CheckFormMode.Record, CheckFormInput(date = "10-04-2026"))
                presenter.addReports()
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(saved).isTrue()
                coVerify(exactly = 1) { attachments.save(any(), byteArrayOf(1)) }
                coVerify(exactly = 1) {
                    wings.save(
                        match {
                            it.history.checks
                                .single()
                                .reports
                                .single()
                                .fileName == "a.pdf"
                        },
                    )
                }
            }

        @Test
        fun `explains a picked file whose name the system cannot handle`() =
            runTest {
                // given
                val path = "/mnt/storage/Fiches de contrôles matériel/report.pdf"
                coEvery { picker.pick("Choose", setOf("pdf")) } throws UnsupportedPathException(path)
                val presenter = presenter(CheckFormMode.Record, CheckFormInput(date = "10-04-2026"))

                // when
                presenter.addReports()

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.UnsupportedPath, path))
            }

        @Test
        fun `completing a planned check removes the plan and records the check`() =
            runTest {
                // given
                val planned = PlannedCheck(PlannedCheckId("plan-1"), LocalDate(2026, 12, 1), CheckType.Partial)
                givenStored(aWing(planned = listOf(planned)))
                val input = CheckFormInput.completing(planned, today = LocalDate(2026, 10, 11))
                val presenter = presenter(CheckFormMode.Complete(planned), input)

                // when
                presenter.submit {}

                // then
                coVerify(exactly = 1) {
                    wings.save(
                        match {
                            it.history.plannedChecks.isEmpty() &&
                                it.history.checks.size == 1
                        },
                    )
                }
            }

        @Test
        fun `does not save when the date is missing`() =
            runTest {
                // given
                val presenter = presenter(CheckFormMode.Record, CheckFormInput())
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(saved).isFalse()
                assertThat(presenter.errors).containsKey(CheckFormField.Date)
                coVerify(exactly = 0) { wings.save(any()) }
            }

        @Test
        fun `shows an error on the date when it lies in the future`() =
            runTest {
                // given
                givenStored()
                val presenter = presenter(CheckFormMode.Record, CheckFormInput(date = "12-10-2026"))

                // when
                presenter.submit {}

                // then
                assertThat(presenter.errors).containsEntry(CheckFormField.Date, CheckFormError.DateInFuture)
            }

        @Test
        fun `keeps the dialog open and shows the failure when saving fails`() =
            runTest {
                // given
                givenStored()
                coEvery { wings.save(any()) } throws IOException("Disk full")
                val presenter = presenter(CheckFormMode.Record, CheckFormInput(date = "10-04-2026"))
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(saved).isFalse()
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.SaveCheck, "Disk full"))
            }
    }

    @Nested
    inner class PlanDialogBehaviour {
        private fun TestScope.presenter(
            editing: PlannedCheck? = null,
            input: PlanFormInput,
        ) = PlanFormPresenter(scope(), useCases(), WING_ID, editing, input)

        @Test
        fun `plans a check`() =
            runTest {
                // given
                givenStored()
                val presenter = presenter(input = PlanFormInput(date = "01-12-2026"))
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(saved).isTrue()
                coVerify(exactly = 1) {
                    wings.save(
                        match {
                            it.history.plannedChecks
                                .single()
                                .date ==
                                LocalDate(2026, 12, 1)
                        },
                    )
                }
            }

        @Test
        fun `shows an error on the date when it lies in the past`() =
            runTest {
                // given
                givenStored()
                val presenter = presenter(input = PlanFormInput(date = "10-10-2026"))

                // when
                presenter.submit {}

                // then
                assertThat(presenter.errors).containsEntry(CheckFormField.Date, PlanFormError.DateInPast)
            }
    }
}
