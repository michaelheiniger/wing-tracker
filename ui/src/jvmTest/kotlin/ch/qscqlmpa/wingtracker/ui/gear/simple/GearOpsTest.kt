package ch.qscqlmpa.wingtracker.ui.gear.simple

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.harness.DeleteHarness
import ch.qscqlmpa.wingtracker.gear.harness.Harness
import ch.qscqlmpa.wingtracker.gear.harness.HarnessDetails
import ch.qscqlmpa.wingtracker.gear.harness.HarnessId
import ch.qscqlmpa.wingtracker.gear.harness.HarnessRepository
import ch.qscqlmpa.wingtracker.gear.harness.ListHarnesses
import ch.qscqlmpa.wingtracker.gear.harness.RegisterHarness
import ch.qscqlmpa.wingtracker.gear.harness.RetireHarness
import ch.qscqlmpa.wingtracker.gear.harness.UpdateHarnessDetails
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.reserve.DeleteReserve
import ch.qscqlmpa.wingtracker.gear.reserve.ListReserves
import ch.qscqlmpa.wingtracker.gear.reserve.RegisterReserve
import ch.qscqlmpa.wingtracker.gear.reserve.Reserve
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveDetails
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveId
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveRepository
import ch.qscqlmpa.wingtracker.gear.reserve.RetireReserve
import ch.qscqlmpa.wingtracker.gear.reserve.UpdateReserveDetails
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.sync.UnsyncedTransactions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private fun details(manufactureDate: LocalDate? = null) =
    SimpleGearDetails(
        name = "Rescue",
        manufacturer = "Gin",
        model = "Yeti Cross",
        size = "XL",
        serialNumber = "GIN-R-7731",
        manufactureDate = manufactureDate,
        acquisition = Acquisition(AcquisitionCondition.New),
        inServiceDate = LocalDate(2024, 3, 1),
        intervalMonths = 12,
    )

class GearOpsTest {
    private val harnesses = mockk<HarnessRepository>(relaxUnitFun = true)
    private val reserves = mockk<ReserveRepository>(relaxUnitFun = true)
    private val harnessOps =
        HarnessOps(
            ListHarnesses(harnesses),
            RegisterHarness(harnesses, UnsyncedTransactions) { HarnessId("new-harness") },
            UpdateHarnessDetails(harnesses, UnsyncedTransactions),
            RetireHarness(harnesses, UnsyncedTransactions),
            DeleteHarness(harnesses, UnsyncedTransactions),
        )
    private val reserveOps =
        ReserveOps(
            ListReserves(reserves),
            RegisterReserve(reserves, UnsyncedTransactions) { ReserveId("new-reserve") },
            UpdateReserveDetails(reserves, UnsyncedTransactions),
            RetireReserve(reserves, UnsyncedTransactions),
            DeleteReserve(reserves, UnsyncedTransactions),
        )

    @Nested
    inner class HarnessOperations {
        @Test
        fun `registers a harness with a months-only interval and drops the manufacture date`() =
            runTest {
                // given / when
                harnessOps.register(details(manufactureDate = LocalDate(2023, 1, 1)))

                // then
                val expected =
                    Harness(
                        HarnessId("new-harness"),
                        HarnessDetails(
                            "Rescue",
                            "Gin",
                            "Yeti Cross",
                            "XL",
                            "GIN-R-7731",
                            Acquisition(AcquisitionCondition.New),
                            LocalDate(2024, 3, 1),
                            MaintenanceInterval(months = 12),
                        ),
                    )
                coVerify(exactly = 1) { harnesses.save(expected) }
            }

        @Test
        fun `lists harnesses as items of the harness kind`() =
            runTest {
                // given
                val harness =
                    Harness(
                        HarnessId("h-1"),
                        HarnessDetails(
                            "Cockpit",
                            "Advance",
                            "Impress 4",
                            null,
                            null,
                            Acquisition(AcquisitionCondition.New),
                            LocalDate(2024, 3, 1),
                        ),
                    )
                coEvery { harnesses.findAll() } returns LoadResult(listOf(harness))

                // when
                val items = harnessOps.list().items

                // then
                assertThat(items.single().ref).isEqualTo(GearRef(GearKind.Harness, "h-1"))
                assertThat(items.single().status).isEqualTo(GearStatus.Active)
                assertThat(items.single().hasChecks).isFalse()
            }
    }

    @Nested
    inner class ReserveOperations {
        @Test
        fun `registers a reserve keeping its manufacture date`() =
            runTest {
                // given / when
                reserveOps.register(details(manufactureDate = LocalDate(2023, 11, 5)))

                // then
                coVerify(exactly = 1) {
                    reserves.save(
                        match {
                            it.details.manufactureDate == LocalDate(2023, 11, 5) &&
                                it.id == ReserveId("new-reserve")
                        },
                    )
                }
            }

        @Test
        fun `lists reserves with their manufacture date`() =
            runTest {
                // given
                val reserve =
                    Reserve(
                        ReserveId("r-1"),
                        ReserveDetails(
                            "Rescue",
                            "Gin",
                            "Yeti Cross",
                            null,
                            null,
                            LocalDate(2023, 11, 5),
                            Acquisition(AcquisitionCondition.New),
                            LocalDate(2024, 3, 1),
                        ),
                    )
                coEvery { reserves.findAll() } returns LoadResult(listOf(reserve))

                // when
                val item = reserveOps.list().items.single()

                // then
                assertThat(item.ref).isEqualTo(GearRef(GearKind.Reserve, "r-1"))
                assertThat(item.details.manufactureDate).isEqualTo(LocalDate(2023, 11, 5))
            }

        @Test
        fun `retires a reserve through its own repository`() =
            runTest {
                // given
                val reserve =
                    Reserve(
                        ReserveId("r-1"),
                        ReserveDetails(
                            "Rescue",
                            "Gin",
                            "Yeti Cross",
                            null,
                            null,
                            null,
                            Acquisition(AcquisitionCondition.New),
                            LocalDate(2024, 3, 1),
                        ),
                    )
                coEvery { reserves.findById(reserve.id) } returns reserve

                // when
                reserveOps.retire(GearRef(GearKind.Reserve, "r-1"), LocalDate(2026, 9, 30), "Sold")

                // then
                coVerify(exactly = 1) {
                    reserves.save(
                        match {
                            it.status ==
                                GearStatus.Retired(LocalDate(2026, 9, 30), "Sold")
                        },
                    )
                }
            }
    }
}
