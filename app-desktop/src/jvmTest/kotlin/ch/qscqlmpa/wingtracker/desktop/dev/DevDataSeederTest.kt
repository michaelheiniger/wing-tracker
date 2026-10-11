package ch.qscqlmpa.wingtracker.desktop.dev

import ch.qscqlmpa.wingtracker.desktop.appModules
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.maintenance.DueState
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GetMaintenanceStatuses
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGearRepository
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

class DevDataSeederTest {
    @TempDir
    lateinit var root: Path

    private fun seeded(): Koin {
        val directory = root.resolve("wing-tracker-dev").createDirectories()
        val koin = koinApplication { modules(appModules(directory)) }.koin
        seedDevData(koin, directory)
        return koin
    }

    @Nested
    inner class Content {
        @Test
        fun `creates every kind of gear, with retired ones among them`() =
            runTest {
                // given / when
                val koin = seeded()

                // then
                val gear = koin.get<MaintainedGearRepository>().findAll()
                assertThat(gear.map { it.ref.kind }.toSet()).containsExactlyInAnyOrder(*GearKind.entries.toTypedArray())
                assertThat(
                    koin
                        .get<WingRepository>()
                        .findAll()
                        .items
                        .count { !it.isActive },
                ).isPositive()
            }

        @Test
        fun `shows ok, due soon and due gear so every reminder state can be seen`() =
            runTest {
                // given / when
                val koin = seeded()

                // then
                val active = koin.get<MaintainedGearRepository>().findAll().filter { it.isActive }
                val states = koin.get<GetMaintenanceStatuses>()().filterKeys { ref -> active.any { it.ref == ref } }
                assertThat(
                    states.values.mapNotNull { it?.state }.toSet(),
                ).containsExactlyInAnyOrder(*DueState.entries.toTypedArray())
            }

        @Test
        fun `logs flights for the wings and imports a few tracks`() =
            runTest {
                // given / when
                val koin = seeded()

                // then
                val flights = koin.get<FlightRepository>().findAll().items
                assertThat(flights).hasSizeGreaterThan(100)
                assertThat(flights.count { it.igcFile != null }).isPositive()
            }

        @Test
        fun `stores the check reports as files`() {
            // given / when
            seeded()

            // then
            assertThat(root.resolve("wing-tracker-dev/attachments").listDirectoryEntries()).isNotEmpty()
        }
    }

    @Nested
    inner class Reset {
        @Test
        fun `refuses to wipe a folder that is not a development folder`() {
            // given
            val real = root.resolve("wing-tracker").createDirectories()
            Files.writeString(real.resolve("keep.txt"), "precious")

            // when / then
            assertThatThrownBy { reset(real) }.isInstanceOf(IllegalStateException::class.java)
            assertThat(real.resolve("keep.txt").exists()).isTrue()
        }

        @Test
        fun `empties a development folder`() {
            // given
            val dev = root.resolve("wing-tracker-dev").createDirectories()
            Files.writeString(dev.resolve("old.txt"), "stale")

            // when
            reset(dev)

            // then
            assertThat(dev.listDirectoryEntries()).isEmpty()
        }
    }
}
