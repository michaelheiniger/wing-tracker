package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.harness.HarnessId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JsonHarnessAndReserveRepositoryTest {
    @TempDir
    lateinit var dataDirectory: Path

    private val harnesses by lazy { JsonHarnessRepository(dataDirectory) }
    private val reserves by lazy { JsonReserveRepository(dataDirectory) }

    private fun check(type: CheckType) =
        MaintenanceCheck(CheckId("c-1"), LocalDate(2025, 3, 1), type, "Gin Service", "Repacked")

    @Nested
    inner class Harnesses {
        @Test
        fun `restores a harness with its history exactly as saved`() =
            runTest {
                // given
                val harness = aStoredHarness(checks = listOf(check(CheckType.Check)))
                harnesses.save(harness)

                // when
                val found = harnesses.findById(harness.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(harness)
            }

        @Test
        fun `restores a retired harness`() =
            runTest {
                // given
                val harness = aStoredHarness(status = GearStatus.Retired(LocalDate(2026, 9, 30), "Replaced"))
                harnesses.save(harness)

                // when
                val found = harnesses.findById(harness.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(harness)
            }

        @Test
        fun `lists the saved harnesses and deletes one`() =
            runTest {
                // given
                harnesses.save(aStoredHarness("h-1"))
                harnesses.save(aStoredHarness("h-2"))

                // when
                harnesses.delete(HarnessId("h-1"))

                // then
                assertThat(harnesses.findAll().items.map { it.id.value }).containsExactly("h-2")
            }

        @Test
        fun `quarantines a corrupt file next to the other harnesses`() =
            runTest {
                // given
                harnesses.save(aStoredHarness("h-1"))
                dataDirectory.resolve("harnesses/h-2.json").writeText("{ broken")

                // when
                val result = harnesses.findAll()

                // then
                assertThat(result.items.map { it.id.value }).containsExactly("h-1")
                assertThat(result.unreadable.single().quarantined).isTrue()
                assertThat(dataDirectory.resolve("quarantine/harnesses/h-2.json").readText()).isEqualTo("{ broken")
            }

        @Test
        fun `keeps a file from a newer version in place`() =
            runTest {
                // given
                harnesses.save(aStoredHarness("h-1"))
                val file = dataDirectory.resolve("harnesses/h-1.json")
                file.rewriteAsNewerVersion()

                // when
                val result = harnesses.findAll()

                // then
                assertThat(result.unreadable.map { it.quarantined }).containsExactly(false)
                assertThat(file.exists()).isTrue()
            }
    }

    @Nested
    inner class Reserves {
        @Test
        fun `restores a reserve with its manufacture date and repack history`() =
            runTest {
                // given
                val reserve = aStoredReserve(checks = listOf(check(CheckType.Repack)))
                reserves.save(reserve)

                // when
                val found = reserves.findById(reserve.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(reserve)
                assertThat(found?.details?.manufactureDate).isEqualTo(LocalDate(2023, 11, 5))
            }

        @Test
        fun `returns null for an unknown reserve`() =
            runTest {
                // given / when
                val found = reserves.findById(ReserveId("unknown"))

                // then
                assertThat(found).isNull()
            }

        @Test
        fun `quarantines a file whose history violates the domain rules`() =
            runTest {
                // given
                reserves.save(aStoredReserve(checks = listOf(check(CheckType.Repack))))
                val file = dataDirectory.resolve("reserves/reserve-1.json")
                file.writeText(file.readText().replace("\"Repack\"", "\"Full\""))

                // when
                val result = reserves.findAll()

                // then
                assertThat(result.items).isEmpty()
                assertThat(result.unreadable.single().quarantined).isTrue()
            }
    }
}
