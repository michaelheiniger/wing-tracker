package ch.qscqlmpa.wingtracker.gear.maintenance

import ch.qscqlmpa.wingtracker.gear.harness.HarnessId
import ch.qscqlmpa.wingtracker.gear.harness.HarnessRepository
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveId
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveRepository
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.gear.wing.aWing
import ch.qscqlmpa.wingtracker.shared.LoadResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CompositeMaintainedGearRepositoryTest {
    private val wings = mockk<WingRepository>(relaxUnitFun = true)
    private val harnesses = mockk<HarnessRepository>(relaxUnitFun = true)
    private val reserves = mockk<ReserveRepository>(relaxUnitFun = true)
    private val repository = CompositeMaintainedGearRepository(wings, harnesses, reserves)
    private val harness = aHarnessWithInterval()

    @Nested
    inner class Find {
        @Test
        fun `looks a wing up in the wing repository`() =
            runTest {
                // given
                val wing = aWing(id = "wing-1")
                coEvery { wings.findById(WingId("wing-1")) } returns wing

                // when
                val found = repository.find(wing.ref)

                // then
                assertThat(found).isEqualTo(wing)
            }

        @Test
        fun `looks a harness up in the harness repository`() =
            runTest {
                // given
                coEvery { harnesses.findById(HarnessId("harness-1")) } returns harness

                // when
                val found = repository.find(harness.ref)

                // then
                assertThat(found).isEqualTo(harness)
            }

        @Test
        fun `returns null for a reserve that does not exist`() =
            runTest {
                // given
                coEvery { reserves.findById(ReserveId("missing")) } returns null

                // when
                val found = repository.find(GearRef(GearKind.Reserve, "missing"))

                // then
                assertThat(found).isNull()
            }
    }

    @Test
    fun `lists the gear of every kind`() =
        runTest {
            // given
            val wing = aWing(id = "wing-1")
            coEvery { wings.findAll() } returns LoadResult(listOf(wing))
            coEvery { harnesses.findAll() } returns LoadResult(listOf(harness))
            coEvery { reserves.findAll() } returns LoadResult(emptyList())

            // when
            val all = repository.findAll()

            // then
            assertThat(all.map { it.ref }).containsExactly(wing.ref, harness.ref)
        }

    @Nested
    inner class Save {
        @Test
        fun `saves a harness in the harness repository only`() =
            runTest {
                // given / when
                repository.save(harness)

                // then
                coVerify(exactly = 1) { harnesses.save(harness) }
                coVerify(exactly = 0) { wings.save(any()) }
            }

        @Test
        fun `saves a wing in the wing repository only`() =
            runTest {
                // given
                val wing = aWing()

                // when
                repository.save(wing)

                // then
                coVerify(exactly = 1) { wings.save(wing) }
                coVerify(exactly = 0) { harnesses.save(any()) }
            }
    }
}
