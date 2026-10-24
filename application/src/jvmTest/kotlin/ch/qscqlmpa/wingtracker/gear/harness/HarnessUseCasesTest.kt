package ch.qscqlmpa.wingtracker.gear.harness

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.wing.GearInUseException
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.RecordingTransactions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private fun aDetails(name: String = "Cockpit") =
    HarnessDetails(
        name = name,
        manufacturer = "Advance",
        model = "Impress 4",
        size = "M",
        serialNumber = "ADV-H-20491",
        acquisition = Acquisition(AcquisitionCondition.New, LocalDate(2024, 2, 20)),
        inServiceDate = LocalDate(2024, 3, 1),
        maintenanceInterval = MaintenanceInterval(months = 24),
    )

private fun aHarness(
    id: String = "harness-1",
    name: String = "Cockpit",
    status: GearStatus = GearStatus.Active,
    checks: List<MaintenanceCheck> = emptyList(),
) = Harness(HarnessId(id), aDetails(name), status, checks)

class HarnessUseCasesTest {
    private val transactions = RecordingTransactions()
    private val harnesses = mockk<HarnessRepository>(relaxUnitFun = true)

    @Nested
    inner class Registering {
        @Test
        fun `records the registration as one change to the logbook`() =
            runTest {
                // given
                val registerHarness = RegisterHarness(harnesses, transactions) { HarnessId("new-harness") }

                // when
                val harness = registerHarness(aDetails())

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.GearRegistered(harness.ref))
            }

        @Test
        fun `saves a new active harness with the generated id`() =
            runTest {
                // given
                val registerHarness = RegisterHarness(harnesses, transactions) { HarnessId("new-harness") }

                // when
                val harness = registerHarness(aDetails())

                // then
                val expected = Harness(HarnessId("new-harness"), aDetails())
                assertThat(harness).usingRecursiveComparison().isEqualTo(expected)
                coVerify(exactly = 1) { harnesses.save(expected) }
            }
    }

    @Nested
    inner class Updating {
        @Test
        fun `records the update as one change to the logbook`() =
            runTest {
                // given
                val existing = aHarness()
                coEvery { harnesses.findById(existing.id) } returns existing

                // when
                UpdateHarnessDetails(harnesses, transactions)(existing.id, aDetails("Pod"))

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.GearDetailsUpdated(existing.ref))
            }

        @Test
        fun `saves the harness with the new details`() =
            runTest {
                // given
                val existing = aHarness()
                coEvery { harnesses.findById(existing.id) } returns existing

                // when
                val updated = UpdateHarnessDetails(harnesses, transactions)(existing.id, aDetails("Pod"))

                // then
                assertThat(updated.details.name).isEqualTo("Pod")
                coVerify(exactly = 1) { harnesses.save(updated) }
            }

        @Test
        fun `fails when the harness does not exist`() =
            runTest {
                // given
                coEvery { harnesses.findById(HarnessId("missing")) } returns null

                // when
                val failure =
                    runCatching {
                        UpdateHarnessDetails(
                            harnesses,
                            transactions,
                        )(HarnessId("missing"), aDetails())
                    }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(HarnessNotFoundException::class.java)
            }
    }

    @Nested
    inner class Retiring {
        @Test
        fun `records the retirement as one change to the logbook`() =
            runTest {
                // given
                val existing = aHarness()
                coEvery { harnesses.findById(existing.id) } returns existing

                // when
                RetireHarness(harnesses, transactions)(existing.id, LocalDate(2026, 9, 30), "Replaced")

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.GearRetired(existing.ref))
            }

        @Test
        fun `saves the harness as retired`() =
            runTest {
                // given
                val existing = aHarness()
                coEvery { harnesses.findById(existing.id) } returns existing

                // when
                val retired = RetireHarness(harnesses, transactions)(existing.id, LocalDate(2026, 9, 30), "Replaced")

                // then
                assertThat(retired.status).isEqualTo(GearStatus.Retired(LocalDate(2026, 9, 30), "Replaced"))
                coVerify(exactly = 1) { harnesses.save(retired) }
            }
    }

    @Nested
    inner class Deleting {
        @Test
        fun `records the deletion as one change to the logbook`() =
            runTest {
                // given
                val existing = aHarness()
                coEvery { harnesses.findById(existing.id) } returns existing

                // when
                DeleteHarness(harnesses, transactions)(existing.id)

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.GearDeleted(existing.ref))
            }

        @Test
        fun `deletes a harness without maintenance history`() =
            runTest {
                // given
                val existing = aHarness()
                coEvery { harnesses.findById(existing.id) } returns existing

                // when
                DeleteHarness(harnesses, transactions)(existing.id)

                // then
                coVerify(exactly = 1) { harnesses.delete(existing.id) }
            }

        @Test
        fun `refuses to delete a harness that has maintenance history`() =
            runTest {
                // given
                val existing =
                    aHarness(checks = listOf(MaintenanceCheck(CheckId("c-1"), LocalDate(2025, 3, 1), CheckType.Check)))
                coEvery { harnesses.findById(existing.id) } returns existing

                // when
                val failure = runCatching { DeleteHarness(harnesses, transactions)(existing.id) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(GearInUseException::class.java)
                coVerify(exactly = 0) { harnesses.delete(any()) }
            }
    }

    @Nested
    inner class Listing {
        @Test
        fun `lists active harnesses first, each group sorted by name ignoring case`() =
            runTest {
                // given
                val retired = aHarness("h-1", "Alpha", GearStatus.Retired(LocalDate(2026, 1, 1)))
                coEvery { harnesses.findAll() } returns
                    LoadResult(listOf(retired, aHarness("h-2", "zeta"), aHarness("h-3", "Beta")))

                // when
                val result = ListHarnesses(harnesses)()

                // then
                assertThat(result.items.map { it.id.value }).containsExactly("h-3", "h-2", "h-1")
            }
    }
}
