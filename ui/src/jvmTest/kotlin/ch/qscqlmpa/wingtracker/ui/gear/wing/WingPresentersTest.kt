package ch.qscqlmpa.wingtracker.ui.gear.wing

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.CompositeMaintainedGearRepository
import ch.qscqlmpa.wingtracker.gear.maintenance.GetMaintenanceStatuses
import ch.qscqlmpa.wingtracker.gear.wing.DeleteWing
import ch.qscqlmpa.wingtracker.gear.wing.ListWings
import ch.qscqlmpa.wingtracker.gear.wing.RegisterWing
import ch.qscqlmpa.wingtracker.gear.wing.RetireWing
import ch.qscqlmpa.wingtracker.gear.wing.UpdateWingDetails
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private fun aWing(
    id: String,
    name: String,
    status: GearStatus = GearStatus.Active,
) = Wing(
    id = WingId(id),
    details =
        WingDetails(
            name = name,
            manufacturer = "Advance",
            model = "Iota 3",
            acquisition = Acquisition(AcquisitionCondition.New),
            inServiceDate = LocalDate(2023, 5, 1),
        ),
    status = status,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WingPresentersTest {
    private val wings = mockk<WingRepository>(relaxUnitFun = true)
    private val flights = mockk<FlightRepository>().also { coEvery { it.findAll() } returns LoadResult(emptyList()) }
    private val today = LocalDate(2026, 10, 11)

    private var hasFlights = false
    private val settings = mockk<SettingsRepository>().also { coEvery { it.load() } returns AppSettings() }

    private fun TestScope.listPresenter() =
        WingListPresenter(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            listWings = ListWings(wings),
            retireWing = RetireWing(wings),
            deleteWing = DeleteWing(wings) { hasFlights },
            getMaintenanceStatuses =
                GetMaintenanceStatuses(
                    CompositeMaintainedGearRepository(wings, mockk(relaxed = true), mockk(relaxed = true)),
                    flights,
                    settings,
                    AppClock(),
                ),
            today = { today },
        )

    @Nested
    inner class WingList {
        @Test
        fun `hides retired wings until asked to show them`() =
            runTest {
                // given
                val active = aWing("w-1", "Sky Dancer")
                val retired = aWing("w-2", "Old Faithful", GearStatus.Retired(LocalDate(2025, 1, 1), "Sold"))
                coEvery { wings.findAll() } returns LoadResult(listOf(active, retired))
                val presenter = listPresenter()

                // when
                presenter.refresh()

                // then
                assertThat(presenter.state.visibleWings.map { it.id.value }).containsExactly("w-1")
                assertThat(presenter.state.retiredCount).isEqualTo(1)

                // when
                presenter.toggleShowRetired()

                // then
                assertThat(presenter.state.visibleWings.map { it.id.value }).containsExactly("w-1", "w-2")
            }

        @Test
        fun `retires a wing today and reloads the list`() =
            runTest {
                // given
                val active = aWing("w-1", "Sky Dancer")
                coEvery { wings.findById(active.id) } returns active
                coEvery { wings.findAll() } returns LoadResult(listOf(active))
                val presenter = listPresenter()
                presenter.refresh()

                // when
                presenter.retire(active.id, "Sold")

                // then
                coVerify { wings.save(active.copy(status = GearStatus.Retired(today, "Sold"))) }
            }

        @Test
        fun `shows the error when loading fails`() =
            runTest {
                // given
                coEvery { wings.findAll() } throws IllegalStateException("Wing w-1 was written by a newer version")
                val presenter = listPresenter()

                // when
                presenter.refresh()

                // then
                assertThat(presenter.state.failure?.kind).isEqualTo(FailureKind.Load)
                assertThat(presenter.state.failure?.detail).contains("newer version")
                assertThat(presenter.state.loading).isFalse()
            }

        @Test
        fun `reports unreadable files until dismissed`() =
            runTest {
                // given
                val unreadable = UnreadableRecord("wing-9", "Unexpected JSON token", quarantined = true)
                coEvery { wings.findAll() } returns LoadResult(listOf(aWing("w-1", "Sky Dancer")), listOf(unreadable))
                val presenter = listPresenter()

                // when
                presenter.refresh()

                // then
                assertThat(presenter.state.unreadable).containsExactly(unreadable)

                // when
                presenter.refresh()

                // then
                assertThat(presenter.state.unreadable).containsExactly(unreadable)

                // when
                presenter.dismissUnreadable()

                // then
                assertThat(presenter.state.unreadable).isEmpty()
            }

        @Test
        fun `deletes a wing and reloads the list`() =
            runTest {
                // given
                val wing = aWing("w-1", "Sky Dancer")
                coEvery { wings.findById(wing.id) } returns wing
                coEvery { wings.findAll() } returns LoadResult(emptyList())
                val presenter = listPresenter()

                // when
                presenter.delete(wing.id)

                // then
                coVerify(exactly = 1) { wings.delete(wing.id) }
                assertThat(presenter.state.wings).isEmpty()
                assertThat(presenter.state.failure).isNull()
            }

        @Test
        fun `explains that a wing with flights can only be retired`() =
            runTest {
                // given
                val wing = aWing("w-1", "Sky Dancer")
                hasFlights = true
                coEvery { wings.findById(wing.id) } returns wing
                val presenter = listPresenter()

                // when
                presenter.delete(wing.id)

                // then
                assertThat(presenter.state.failure?.kind).isEqualTo(FailureKind.DeleteInUse)
                coVerify(exactly = 0) { wings.delete(any()) }
            }
    }

    @Nested
    inner class WingFormPresenterBehaviour {
        private fun TestScope.formPresenter(editing: Wing? = null) =
            WingFormPresenter(
                scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
                registerWing = RegisterWing(wings) { WingId("new-wing") },
                updateWingDetails = UpdateWingDetails(wings),
                editing = editing,
            )

        private val validInput =
            WingFormInput(
                name = "Sky Dancer",
                manufacturer = "Advance",
                model = "Iota 3",
                inServiceDate = LocalDate(2023, 5, 1),
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
                assertThat(presenter.errors).containsKey(WingFormField.Name)
                assertThat(saved).isFalse()
                coVerify(exactly = 0) { wings.save(any()) }
            }

        @Test
        fun `registers a new wing when the form is valid`() =
            runTest {
                // given
                val presenter = formPresenter()
                presenter.change { validInput }
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(saved).isTrue()
                val expected = Wing(WingId("new-wing"), (validInput.validate() as WingFormResult.Valid).details)
                coVerify(exactly = 1) { wings.save(expected) }
            }

        @Test
        fun `updates the existing wing when editing`() =
            runTest {
                // given
                val existing = aWing("w-1", "Sky Dancer")
                coEvery { wings.findById(existing.id) } returns existing
                val presenter = formPresenter(editing = existing)
                presenter.change { it.copy(name = "Cloud Surfer") }

                // when
                presenter.submit {}

                // then
                val expected = existing.copy(details = existing.details.copy(name = "Cloud Surfer"))
                coVerify(exactly = 1) { wings.save(expected) }
            }

        @Test
        fun `keeps the form open and shows the error when saving fails`() =
            runTest {
                // given
                coEvery { wings.save(any()) } throws java.io.IOException("Disk full")
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
