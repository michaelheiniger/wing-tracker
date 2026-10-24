package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.aStoredCheck
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
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

class WingUseCasesTest {
    private val transactions = RecordingTransactions()
    private val wings = mockk<WingRepository>(relaxUnitFun = true)

    @Nested
    inner class RegisterWingUseCase {
        @Test
        fun `records the registration as one change to the logbook`() =
            runTest {
                // given
                val registerWing = RegisterWing(wings, transactions) { WingId("new-wing-1") }

                // when
                val wing = registerWing(aWingDetails())

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.GearRegistered(wing.ref))
            }

        @Test
        fun `saves a new active wing with the generated id`() =
            runTest {
                // given
                val details = aWingDetails()
                val registerWing = RegisterWing(wings, transactions) { WingId("new-wing-1") }

                // when
                val wing = registerWing(details)

                // then
                val expected = Wing(id = WingId("new-wing-1"), details = details, status = GearStatus.Active)
                assertThat(wing).usingRecursiveComparison().isEqualTo(expected)
                coVerify(exactly = 1) { wings.save(expected) }
            }
    }

    @Nested
    inner class UpdateWingDetailsUseCase {
        @Test
        fun `records the update as one change to the logbook`() =
            runTest {
                // given
                val existing = aWing()
                coEvery { wings.findById(existing.id) } returns existing

                // when
                UpdateWingDetails(wings, transactions)(existing.id, aWingDetails(name = "Cloud Surfer"))

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.GearDetailsUpdated(existing.ref))
            }

        @Test
        fun `saves the wing with the new details`() =
            runTest {
                // given
                val existing = aWing()
                val newDetails = aWingDetails(name = "Cloud Surfer")
                coEvery { wings.findById(existing.id) } returns existing

                // when
                val updated = UpdateWingDetails(wings, transactions)(existing.id, newDetails)

                // then
                assertThat(updated.details.name).isEqualTo("Cloud Surfer")
                coVerify(exactly = 1) { wings.save(existing.copy(details = newDetails)) }
            }

        @Test
        fun `fails when the wing does not exist`() =
            runTest {
                // given
                val id = WingId("missing-wing")
                coEvery { wings.findById(id) } returns null

                // when / then
                val failure =
                    runCatching {
                        UpdateWingDetails(
                            wings,
                            transactions,
                        )(id, aWingDetails())
                    }.exceptionOrNull()
                assertThat(failure).isInstanceOf(WingNotFoundException::class.java)
                coVerify(exactly = 0) { wings.save(any()) }
            }
    }

    @Nested
    inner class RetireWingUseCase {
        @Test
        fun `records the retirement as one change to the logbook`() =
            runTest {
                // given
                val existing = aWing()
                coEvery { wings.findById(existing.id) } returns existing

                // when
                RetireWing(wings, transactions)(existing.id, LocalDate(2026, 9, 30), "Sold")

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.GearRetired(existing.ref))
            }

        @Test
        fun `saves the wing as retired`() =
            runTest {
                // given
                val existing = aWing()
                coEvery { wings.findById(existing.id) } returns existing

                // when
                val retired = RetireWing(wings, transactions)(existing.id, LocalDate(2026, 9, 30), "Sold")

                // then
                assertThat(retired.status).isEqualTo(GearStatus.Retired(LocalDate(2026, 9, 30), "Sold"))
                coVerify(exactly = 1) { wings.save(retired) }
            }

        @Test
        fun `fails when the wing does not exist`() =
            runTest {
                // given
                val id = WingId("missing-wing")
                coEvery { wings.findById(id) } returns null

                // when / then
                val failure =
                    runCatching {
                        RetireWing(
                            wings,
                            transactions,
                        )(id, LocalDate(2026, 9, 30), null)
                    }.exceptionOrNull()
                assertThat(failure).isInstanceOf(WingNotFoundException::class.java)
            }
    }

    @Nested
    inner class DeleteWingUseCase {
        @Test
        fun `records the deletion as one change to the logbook`() =
            runTest {
                // given
                val existing = aWing()
                coEvery { wings.findById(existing.id) } returns existing

                // when
                DeleteWing(wings, { false }, transactions)(existing.id)

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.GearDeleted(existing.ref))
            }

        @Test
        fun `deletes a wing without flights`() =
            runTest {
                // given
                val existing = aWing()
                coEvery { wings.findById(existing.id) } returns existing

                // when
                DeleteWing(wings, { false }, transactions)(existing.id)

                // then
                coVerify(exactly = 1) { wings.delete(existing.id) }
            }

        @Test
        fun `refuses to delete a wing that has flights`() =
            runTest {
                // given
                val existing = aWing()
                coEvery { wings.findById(existing.id) } returns existing

                // when
                val failure = runCatching { DeleteWing(wings, { true }, transactions)(existing.id) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(WingInUseException::class.java)
                coVerify(exactly = 0) { wings.delete(any()) }
            }

        @Test
        fun `refuses to delete a wing that has maintenance history`() =
            runTest {
                // given
                val existing = aWing(checks = listOf(aStoredCheck()))
                coEvery { wings.findById(existing.id) } returns existing

                // when
                val failure = runCatching { DeleteWing(wings, { false }, transactions)(existing.id) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(WingInUseException::class.java)
                coVerify(exactly = 0) { wings.delete(any()) }
            }

        @Test
        fun `fails when the wing does not exist`() =
            runTest {
                // given
                val id = WingId("missing-wing")
                coEvery { wings.findById(id) } returns null

                // when
                val failure = runCatching { DeleteWing(wings, { false }, transactions)(id) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(WingNotFoundException::class.java)
                coVerify(exactly = 0) { wings.delete(any()) }
            }
    }

    @Nested
    inner class ListWingsUseCase {
        @Test
        fun `passes unreadable records through so the user can be warned`() =
            runTest {
                // given
                val unreadable = UnreadableRecord("broken-wing", "Unexpected JSON token", quarantined = true)
                coEvery { wings.findAll() } returns LoadResult(listOf(aWing()), listOf(unreadable))

                // when
                val result = ListWings(wings)()

                // then
                assertThat(result.unreadable).containsExactly(unreadable)
            }

        @Test
        fun `lists active wings first, each group sorted by name ignoring case`() =
            runTest {
                // given
                val retiredStatus = GearStatus.Retired(LocalDate(2026, 1, 1))
                val retired = aWing(id = "w-1", details = aWingDetails("Alpha"), status = retiredStatus)
                val zeta = aWing(id = "w-2", details = aWingDetails("zeta"))
                val beta = aWing(id = "w-3", details = aWingDetails("Beta"))
                coEvery { wings.findAll() } returns LoadResult(listOf(retired, zeta, beta))

                // when
                val result = ListWings(wings)()

                // then
                assertThat(result.items.map { it.id.value }).containsExactly("w-3", "w-2", "w-1")
            }
    }
}
