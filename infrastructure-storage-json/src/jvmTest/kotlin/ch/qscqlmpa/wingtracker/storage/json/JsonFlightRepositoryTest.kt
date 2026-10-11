package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.IgcFile
import kotlinx.coroutines.test.runTest
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

class JsonFlightRepositoryTest {
    @TempDir
    lateinit var dataDirectory: Path

    private val repository by lazy { JsonFlightRepository(dataDirectory) }

    @Nested
    inner class SaveAndFindById {
        @Test
        fun `restores a fully populated flight exactly as saved`() =
            runTest {
                // given
                val flight = aStoredFlight()
                repository.save(flight)

                // when
                val found = repository.findById(flight.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(flight)
            }

        @Test
        fun `restores a flight without sites and notes`() =
            runTest {
                // given
                val full = aStoredFlight()
                val flight =
                    full.copy(
                        details = full.details.copy(takeOffSite = null, landingSite = null, notes = null),
                    )
                repository.save(flight)

                // when
                val found = repository.findById(flight.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(flight)
            }

        @Test
        fun `restores the imported track with its checksum`() =
            runTest {
                // given
                val imported =
                    aStoredFlight().copy(
                        igcFile =
                            IgcFile(
                                Attachment(AttachmentId("igc-1"), "2026-09-20-niederhorn.igc"),
                                "3b7e72f0c1d9",
                            ),
                    )
                repository.save(imported)

                // when
                val found = repository.findById(imported.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(imported)
            }

        @Test
        fun `reads a flight written before tracks could be imported`() =
            runTest {
                // given
                val flight = aStoredFlight()
                repository.save(flight)
                val file = dataDirectory.resolve("flights/${flight.id.value}.json")
                val newerKeys = setOf("igcAttachmentId", "igcFileName", "igcChecksum")
                val document = Json.parseToJsonElement(file.readText()).jsonObject
                file.writeText(
                    JsonObject(
                        document.filterKeys { it !in newerKeys } + ("schemaVersion" to JsonPrimitive(1)),
                    ).toString(),
                )

                // when
                val found = repository.findById(flight.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(flight)
                assertThat(found?.igcFile).isNull()
            }

        @Test
        fun `stores each flight in its own file`() =
            runTest {
                // given
                val flight = aStoredFlight()

                // when
                repository.save(flight)

                // then
                val files = dataDirectory.resolve("flights").listDirectoryEntries().map { it.fileName.toString() }
                assertThat(files).containsExactly("${flight.id.value}.json")
            }

        @Test
        fun `returns null for an unknown id`() =
            runTest {
                // given / when
                val found = repository.findById(FlightId("unknown"))

                // then
                assertThat(found).isNull()
            }
    }

    @Nested
    inner class FindAll {
        @Test
        fun `returns every saved flight`() =
            runTest {
                // given
                val first = aStoredFlight(id = "flight-1")
                val second = aStoredFlight(id = "flight-2", notes = null)
                repository.save(first)
                repository.save(second)

                // when
                val result = repository.findAll()

                // then
                assertThat(
                    result.items,
                ).usingRecursiveFieldByFieldElementComparator().containsExactlyInAnyOrder(first, second)
            }

        @Test
        fun `quarantines a corrupt file and still loads the others`() =
            runTest {
                // given
                val good = aStoredFlight(id = "flight-1")
                repository.save(good)
                dataDirectory.resolve("flights/flight-2.json").writeText("{ broken")

                // when
                val result = repository.findAll()

                // then
                assertThat(result.items).usingRecursiveFieldByFieldElementComparator().containsExactly(good)
                assertThat(result.unreadable.map { it.name to it.quarantined }).containsExactly("flight-2" to true)
                assertThat(dataDirectory.resolve("quarantine/flights/flight-2.json").readText()).isEqualTo("{ broken")
            }

        @Test
        fun `quarantines a file that violates the domain rules`() =
            runTest {
                // given
                repository.save(aStoredFlight(id = "flight-1"))
                val file = dataDirectory.resolve("flights/flight-1.json")
                file.writeText(file.readText().replace("\"durationMinutes\": 95", "\"durationMinutes\": 0"))

                // when
                val result = repository.findAll()

                // then
                assertThat(result.items).isEmpty()
                assertThat(result.unreadable.single().quarantined).isTrue()
            }

        @Test
        fun `keeps a file from a newer version in place`() =
            runTest {
                // given
                repository.save(aStoredFlight(id = "flight-1"))
                val file = dataDirectory.resolve("flights/flight-1.json")
                file.writeText(file.readText().replace("\"schemaVersion\": 2", "\"schemaVersion\": 999"))

                // when
                val result = repository.findAll()

                // then
                assertThat(result.unreadable.map { it.quarantined }).containsExactly(false)
                assertThat(file.exists()).isTrue()
            }
    }

    @Nested
    inner class Delete {
        @Test
        fun `removes the flight`() =
            runTest {
                // given
                val flight = aStoredFlight()
                repository.save(flight)

                // when
                repository.delete(flight.id)

                // then
                assertThat(repository.findById(flight.id)).isNull()
            }
    }
}
