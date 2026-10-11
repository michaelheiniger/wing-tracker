package ch.qscqlmpa.wingtracker.gear.reserve

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.wing.GearInUseException
import ch.qscqlmpa.wingtracker.shared.LoadResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private fun aDetails(name: String = "Rescue") =
    ReserveDetails(
        name = name,
        manufacturer = "Gin",
        model = "Yeti Cross",
        size = "XL",
        serialNumber = "GIN-R-7731",
        manufactureDate = LocalDate(2023, 11, 5),
        acquisition = Acquisition(AcquisitionCondition.New, LocalDate(2024, 2, 20)),
        inServiceDate = LocalDate(2024, 3, 1),
        maintenanceInterval = MaintenanceInterval(months = 12),
    )

private fun aReserve(
    id: String = "reserve-1",
    name: String = "Rescue",
    status: GearStatus = GearStatus.Active,
    checks: List<MaintenanceCheck> = emptyList(),
) = Reserve(ReserveId(id), aDetails(name), status, checks)

class ReserveUseCasesTest {
    private val reservees = mockk<ReserveRepository>(relaxUnitFun = true)

    @Nested
    inner class Registering {
        @Test
        fun `saves a new active reserve with the generated id`() =
            runTest {
                // given
                val registerReserve = RegisterReserve(reservees) { ReserveId("new-reserve") }

                // when
                val reserve = registerReserve(aDetails())

                // then
                val expected = Reserve(ReserveId("new-reserve"), aDetails())
                assertThat(reserve).usingRecursiveComparison().isEqualTo(expected)
                coVerify(exactly = 1) { reservees.save(expected) }
            }
    }

    @Nested
    inner class Updating {
        @Test
        fun `saves the reserve with the new details`() =
            runTest {
                // given
                val existing = aReserve()
                coEvery { reservees.findById(existing.id) } returns existing

                // when
                val updated = UpdateReserveDetails(reservees)(existing.id, aDetails("Backup"))

                // then
                assertThat(updated.details.name).isEqualTo("Backup")
                coVerify(exactly = 1) { reservees.save(updated) }
            }

        @Test
        fun `fails when the reserve does not exist`() =
            runTest {
                // given
                coEvery { reservees.findById(ReserveId("missing")) } returns null

                // when
                val failure =
                    runCatching {
                        UpdateReserveDetails(
                            reservees,
                        )(ReserveId("missing"), aDetails())
                    }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(ReserveNotFoundException::class.java)
            }
    }

    @Nested
    inner class Retiring {
        @Test
        fun `saves the reserve as retired`() =
            runTest {
                // given
                val existing = aReserve()
                coEvery { reservees.findById(existing.id) } returns existing

                // when
                val retired = RetireReserve(reservees)(existing.id, LocalDate(2026, 9, 30), "Sold")

                // then
                assertThat(retired.status).isEqualTo(GearStatus.Retired(LocalDate(2026, 9, 30), "Sold"))
                coVerify(exactly = 1) { reservees.save(retired) }
            }
    }

    @Nested
    inner class Deleting {
        @Test
        fun `deletes a reserve without maintenance history`() =
            runTest {
                // given
                val existing = aReserve()
                coEvery { reservees.findById(existing.id) } returns existing

                // when
                DeleteReserve(reservees)(existing.id)

                // then
                coVerify(exactly = 1) { reservees.delete(existing.id) }
            }

        @Test
        fun `refuses to delete a reserve that has maintenance history`() =
            runTest {
                // given
                val existing =
                    aReserve(checks = listOf(MaintenanceCheck(CheckId("c-1"), LocalDate(2025, 3, 1), CheckType.Repack)))
                coEvery { reservees.findById(existing.id) } returns existing

                // when
                val failure = runCatching { DeleteReserve(reservees)(existing.id) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(GearInUseException::class.java)
                coVerify(exactly = 0) { reservees.delete(any()) }
            }
    }

    @Nested
    inner class Listing {
        @Test
        fun `lists active reservees first, each group sorted by name ignoring case`() =
            runTest {
                // given
                val retired = aReserve("h-1", "Alpha", GearStatus.Retired(LocalDate(2026, 1, 1)))
                coEvery { reservees.findAll() } returns
                    LoadResult(listOf(retired, aReserve("h-2", "zeta"), aReserve("h-3", "Beta")))

                // when
                val result = ListReserves(reservees)()

                // then
                assertThat(result.items.map { it.id.value }).containsExactly("h-3", "h-2", "h-1")
            }
    }
}
