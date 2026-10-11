package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.hours

class JsonWingRepositoryTest {
    @TempDir
    lateinit var dataDirectory: Path

    private val repository by lazy { JsonWingRepository(dataDirectory) }

    @Nested
    inner class SaveAndFindById {
        @Test
        fun `restores a fully populated wing exactly as saved`() =
            runTest {
                // given
                val wing = aStoredWing()
                repository.save(wing)

                // when
                val found = repository.findById(wing.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(wing)
            }

        @Test
        fun `restores the interval, the check history with its reports and the planned checks`() =
            runTest {
                // given
                val wing =
                    aStoredWing(
                        interval = MaintenanceInterval(months = 24, hours = 100.hours),
                        checks =
                            listOf(
                                aStoredCheck("check-2", CheckType.Other, restartsInterval = true),
                                aStoredCheck("check-1", CheckType.Full),
                            ),
                        plannedChecks = listOf(aStoredPlannedCheck()),
                    )
                repository.save(wing)

                // when
                val found = repository.findById(wing.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(wing)
            }

        @Test
        fun `reads a wing written before maintenance existed`() =
            runTest {
                // given
                val wing = aStoredWing()
                repository.save(wing)
                val file = dataDirectory.resolve("wings/${wing.id.value}.json")
                val newerKeys =
                    setOf("maintenanceIntervalMonths", "maintenanceIntervalHoursMinutes", "checks", "plannedChecks")
                val document = Json.parseToJsonElement(file.readText()).jsonObject
                val legacy =
                    JsonObject(
                        document.filterKeys { it !in newerKeys } + ("schemaVersion" to JsonPrimitive(1)),
                    ).toString()
                file.writeText(legacy)

                // when
                val found = repository.findById(wing.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(wing)
            }

        @Test
        fun `restores a retired wing with its date and reason`() =
            runTest {
                // given
                val wing = aStoredWing(status = GearStatus.Retired(LocalDate(2026, 9, 30), "Sold"))
                repository.save(wing)

                // when
                val found = repository.findById(wing.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(wing)
            }

        @Test
        fun `restores a wing without optional attributes`() =
            runTest {
                // given
                val full = aStoredWing()
                val wing =
                    full.copy(
                        details =
                            full.details.copy(
                                size = null,
                                serialNumber = null,
                                certificationClass = null,
                                acquisition = Acquisition(AcquisitionCondition.New),
                            ),
                    )
                repository.save(wing)

                // when
                val found = repository.findById(wing.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(wing)
            }

        @Test
        fun `overwrites the previous version of the same wing`() =
            runTest {
                // given
                repository.save(aStoredWing(name = "Sky Dancer"))
                repository.save(aStoredWing(name = "Cloud Surfer"))

                // when
                val found = repository.findById(WingId("3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47"))

                // then
                assertThat(found?.details?.name).isEqualTo("Cloud Surfer")
            }

        @Test
        fun `returns null for an unknown id`() =
            runTest {
                // given / when
                val found = repository.findById(WingId("unknown"))

                // then
                assertThat(found).isNull()
            }

        @Test
        fun `stores each wing in its own file and leaves no temporary file`() =
            runTest {
                // given
                val wing = aStoredWing()

                // when
                repository.save(wing)

                // then
                val files = dataDirectory.resolve("wings").listDirectoryEntries().map { it.fileName.toString() }
                assertThat(files).containsExactly("${wing.id.value}.json")
            }

        @Test
        fun `fails to read a wing written by a newer schema version`() =
            runTest {
                // given
                repository.save(aStoredWing())
                val file = dataDirectory.resolve("wings/3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47.json")
                file.writeText(file.readText().replace("\"schemaVersion\": 2", "\"schemaVersion\": 999"))

                // when
                val failure = runCatching { repository.findById(WingId("3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47")) }

                // then
                assertThat(failure.exceptionOrNull())
                    .isInstanceOf(IllegalStateException::class.java)
                    .hasMessageContaining("newer version")
            }
    }

    @Nested
    inner class FindAll {
        @Test
        fun `returns an empty list when nothing was saved yet`() =
            runTest {
                // given / when
                val wings = repository.findAll()

                // then
                assertThat(wings.items).isEmpty()
                assertThat(dataDirectory.resolve("wings").exists()).isFalse()
            }

        @Test
        fun `returns every saved wing`() =
            runTest {
                // given
                val first = aStoredWing(id = "wing-1", name = "Sky Dancer")
                val second = aStoredWing(id = "wing-2", name = "Cloud Surfer")
                repository.save(first)
                repository.save(second)

                // when
                val wings = repository.findAll()

                // then
                assertThat(
                    wings.items,
                ).usingRecursiveFieldByFieldElementComparator().containsExactlyInAnyOrder(first, second)
            }
    }

    @Nested
    inner class UnreadableFiles {
        private fun writeRaw(
            name: String,
            content: String,
        ) {
            val directory = dataDirectory.resolve("wings")
            directory.toFile().mkdirs()
            directory.resolve("$name.json").writeText(content)
        }

        @Test
        fun `loads the readable wings and quarantines a corrupt file`() =
            runTest {
                // given
                val good = aStoredWing(id = "wing-1")
                repository.save(good)
                writeRaw("wing-2", "{ this is not json")

                // when
                val result = repository.findAll()

                // then
                assertThat(result.items).usingRecursiveFieldByFieldElementComparator().containsExactly(good)
                assertThat(result.unreadable.map { it.name to it.quarantined }).containsExactly("wing-2" to true)
                assertThat(dataDirectory.resolve("wings/wing-2.json").exists()).isFalse()
                val quarantined = dataDirectory.resolve("quarantine/wings/wing-2.json")
                assertThat(quarantined.readText()).isEqualTo("{ this is not json")
            }

        @Test
        fun `quarantines a file that is valid json but violates the domain rules`() =
            runTest {
                // given
                repository.save(aStoredWing(id = "wing-1"))
                val file = dataDirectory.resolve("wings/wing-1.json")
                file.writeText(file.readText().replace("\"name\": \"Sky Dancer\"", "\"name\": \"\""))

                // when
                val result = repository.findAll()

                // then
                assertThat(result.items).isEmpty()
                assertThat(result.unreadable.single().quarantined).isTrue()
            }

        @Test
        fun `keeps a file from a newer version in place and only warns`() =
            runTest {
                // given
                repository.save(aStoredWing(id = "wing-1"))
                val file = dataDirectory.resolve("wings/wing-1.json")
                file.writeText(file.readText().replace("\"schemaVersion\": 2", "\"schemaVersion\": 999"))

                // when
                val result = repository.findAll()

                // then
                assertThat(result.unreadable.map { it.quarantined }).containsExactly(false)
                assertThat(file.exists()).isTrue()
            }

        @Test
        fun `never overwrites an earlier quarantined file with the same name`() =
            runTest {
                // given
                writeRaw("wing-2", "first corrupt version")
                repository.findAll()
                writeRaw("wing-2", "second corrupt version")

                // when
                repository.findAll()

                // then
                val quarantined = dataDirectory.resolve("quarantine/wings").listDirectoryEntries().map { it.readText() }
                assertThat(quarantined).containsExactlyInAnyOrder("first corrupt version", "second corrupt version")
            }
    }

    @Nested
    inner class Delete {
        @Test
        fun `removes the file of the wing`() =
            runTest {
                // given
                val wing = aStoredWing()
                repository.save(wing)

                // when
                repository.delete(wing.id)

                // then
                assertThat(repository.findById(wing.id)).isNull()
            }

        @Test
        fun `does nothing when the wing is already gone`() =
            runTest {
                // given / when
                val failure = runCatching { repository.delete(WingId("unknown")) }

                // then
                assertThat(failure.isSuccess).isTrue()
            }
    }
}
