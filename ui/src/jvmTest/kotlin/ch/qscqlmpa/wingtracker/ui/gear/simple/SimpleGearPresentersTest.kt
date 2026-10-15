package ch.qscqlmpa.wingtracker.ui.gear.simple

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.GetMaintenanceStatuses
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGearRepository
import ch.qscqlmpa.wingtracker.gear.wing.GearInUseException
import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.undo.TEXT
import ch.qscqlmpa.wingtracker.ui.undo.UndoCoordinator
import ch.qscqlmpa.wingtracker.ui.undo.expire
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException

private fun aDetails(name: String = "Cockpit") =
    SimpleGearDetails(
        name = name,
        manufacturer = "Advance",
        model = "Impress 4",
        acquisition = Acquisition(AcquisitionCondition.New),
        inServiceDate = LocalDate(2024, 3, 1),
        intervalMonths = 24,
    )

private fun anItem(
    id: String,
    name: String = "Cockpit",
    status: GearStatus = GearStatus.Active,
) = SimpleGearItem(GearRef(GearKind.Harness, id), aDetails(name), status, hasChecks = false, nextPlannedCheck = null)

@OptIn(ExperimentalCoroutinesApi::class)
class SimpleGearPresentersTest {
    private val ops = mockk<SimpleGearOps>(relaxUnitFun = true).also { every { it.kind } returns GearKind.Harness }
    private val gear = mockk<MaintainedGearRepository>().also { coEvery { it.findAll() } returns emptyList() }
    private val flights = mockk<FlightRepository>().also { coEvery { it.findAll() } returns LoadResult(emptyList()) }
    private val today = LocalDate(2026, 10, 11)
    private val settings = mockk<SettingsRepository>().also { coEvery { it.load() } returns AppSettings() }

    private fun TestScope.listPresenter() =
        SimpleGearListPresenter(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            ops = ops,
            getMaintenanceStatuses = GetMaintenanceStatuses(gear, flights, settings, AppClock()),
            today = { today },
        )

    private fun TestScope.formPresenter(editing: SimpleGearItem? = null) =
        SimpleGearFormPresenter(TestScope(UnconfinedTestDispatcher(testScheduler)), ops, editing)

    @Nested
    inner class ListBehaviour {
        @Test
        fun `hides retired items until asked to show them`() =
            runTest {
                // given
                val retired = anItem("h-2", "Old", GearStatus.Retired(LocalDate(2025, 1, 1)))
                coEvery { ops.list() } returns LoadResult(listOf(anItem("h-1"), retired))
                val presenter = listPresenter()

                // when
                presenter.refresh()

                // then
                assertThat(presenter.state.visibleItems.map { it.ref.id }).containsExactly("h-1")
                assertThat(presenter.state.retiredCount).isEqualTo(1)

                // when
                presenter.toggleShowRetired()

                // then
                assertThat(presenter.state.visibleItems.map { it.ref.id }).containsExactly("h-1", "h-2")
            }

        @Test
        fun `retires an item today and reloads`() =
            runTest {
                // given
                coEvery { ops.list() } returns LoadResult(listOf(anItem("h-1")))
                val presenter = listPresenter()

                // when
                presenter.retire(GearRef(GearKind.Harness, "h-1"), "Replaced")

                // then
                coVerify(exactly = 1) { ops.retire(GearRef(GearKind.Harness, "h-1"), today, "Replaced") }
            }

        @Test
        fun `explains that an item with history can only be retired`() =
            runTest {
                // given
                coEvery { ops.delete(any()) } throws object : GearInUseException("in use") {}
                val presenter = listPresenter()

                // when
                val undo = UndoCoordinator()
                presenter.delete(GearRef(GearKind.Harness, "h-1"), undo, TEXT)
                undo.expire()

                // then
                assertThat(presenter.state.failure?.kind).isEqualTo(FailureKind.DeleteInUse)
            }

        @Test
        fun `shows the failure when loading fails`() =
            runTest {
                // given
                coEvery { ops.list() } throws IOException("disk unreadable")
                val presenter = listPresenter()

                // when
                presenter.refresh()

                // then
                assertThat(presenter.state.failure).isEqualTo(Failure(FailureKind.Load, "disk unreadable"))
            }

        @Test
        fun `reports unreadable files until dismissed`() =
            runTest {
                // given
                val broken = UnreadableRecord("h-9", "bad json", quarantined = true)
                coEvery { ops.list() } returns LoadResult(emptyList(), listOf(broken))
                val presenter = listPresenter()

                // when
                presenter.refresh()
                presenter.refresh()

                // then
                assertThat(presenter.state.unreadable).containsExactly(broken)

                // when
                presenter.dismissUnreadable()

                // then
                assertThat(presenter.state.unreadable).isEmpty()
            }
    }

    @Nested
    inner class FormBehaviour {
        private val validInput =
            SimpleGearFormInput(
                name = "Cockpit",
                manufacturer = "Advance",
                model = "Impress 4",
                inServiceDate = "01-03-2024",
            )

        @Test
        fun `does not save and exposes errors when the form is invalid`() =
            runTest {
                // given
                val presenter = formPresenter()
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(presenter.errors).containsKey(SimpleGearField.Name)
                assertThat(saved).isFalse()
                coVerify(exactly = 0) { ops.register(any()) }
            }

        @Test
        fun `registers a new item when the form is valid`() =
            runTest {
                // given
                val presenter = formPresenter()
                presenter.change { validInput }
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(saved).isTrue()
                coVerify(exactly = 1) { ops.register(match { it.name == "Cockpit" }) }
            }

        @Test
        fun `updates the existing item when editing`() =
            runTest {
                // given
                val existing = anItem("h-1")
                val presenter = formPresenter(editing = existing)
                presenter.change { it.copy(name = "Pod") }

                // when
                presenter.submit {}

                // then
                coVerify(exactly = 1) { ops.update(existing.ref, match { it.name == "Pod" }) }
            }

        @Test
        fun `keeps the form open and shows the failure when saving fails`() =
            runTest {
                // given
                coEvery { ops.register(any()) } throws IOException("Disk full")
                val presenter = formPresenter()
                presenter.change { validInput }
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(saved).isFalse()
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.Save, "Disk full"))
                assertThat(presenter.saving).isFalse()
            }
    }
}
