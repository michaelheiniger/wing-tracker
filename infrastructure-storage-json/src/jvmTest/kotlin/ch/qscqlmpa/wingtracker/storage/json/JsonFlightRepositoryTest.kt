package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.flights.ExternalReference
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightType
import ch.qscqlmpa.wingtracker.flights.IgcFile
import ch.qscqlmpa.wingtracker.flights.SiteNames
import ch.qscqlmpa.wingtracker.flights.SiteSource
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
import kotlin.time.Instant

class JsonFlightRepositoryTest {
    @TempDir
    lateinit var dataDirectory: Path

    private val repository by lazy { JsonFlightRepository(dataDirectory) }

    /** Rewrites the stored [flight] the way [schemaVersion] wrote it: without [newerKeys], one name per site. */
    private fun rewriteAsSchema(
        schemaVersion: Int,
        flight: Flight,
        newerKeys: Set<String> = emptySet(),
    ) {
        val file = dataDirectory.resolve("flights/${flight.id.value}.json")
        val document = Json.parseToJsonElement(file.readText()).jsonObject
        val legacy =
            mapOf(
                "schemaVersion" to JsonPrimitive(schemaVersion),
                "takeOffSite" to JsonPrimitive(flight.details.takeOffSite.shown),
                "landingSite" to JsonPrimitive(flight.details.landingSite.shown),
            )
        val dropped = newerKeys + setOf("takeOffSiteNames", "landingSiteNames")
        file.writeText(JsonObject(document.filterKeys { it !in dropped } + legacy).toString())
    }

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
                        details =
                            full.details.copy(
                                takeOffSite = SiteNames.NONE,
                                landingSite = SiteNames.NONE,
                                notes = null,
                            ),
                    )
                repository.save(flight)

                // when
                val found = repository.findById(flight.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(flight)
            }

        @Test
        fun `restores the site names of every source`() =
            runTest {
                // given
                val base = aStoredFlight()
                val flight =
                    base.copy(
                        details =
                            base.details.copy(
                                takeOffSite =
                                    SiteNames(
                                        mapOf(
                                            SiteSource.Manual to "Niederhorn West",
                                            SiteSource.Syride to "Niederhorn",
                                            SiteSource.Spreadsheet to "Niederhorn (BE)",
                                        ),
                                    ),
                                landingSite = SiteNames.of(SiteSource.Spreadsheet, "Beatenbucht"),
                            ),
                    )
                repository.save(flight)

                // when
                val found = repository.findById(flight.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(flight)
            }

        @Test
        fun `reads a flight written before the type was kept as of unknown type`() =
            runTest {
                // given
                val flight = aStoredFlight()
                repository.save(flight.copy(details = flight.details.copy(type = FlightType.Site)))
                rewriteAsSchema(6, flight, newerKeys = setOf("type"))

                // when
                val found = repository.findById(flight.id)

                // then
                assertThat(found?.details?.type).isNull()
            }

        @Test
        fun `reads a site name written before names had a source as typed by the pilot`() =
            runTest {
                // given
                val flight = aStoredFlight()
                repository.save(flight)
                rewriteAsSchema(5, flight)

                // when
                val found = repository.findById(flight.id)

                // then
                assertThat(found?.details?.takeOffSite).isEqualTo(SiteNames.of(SiteSource.Manual, "Niederhorn"))
                assertThat(found?.details?.landingSite).isEqualTo(SiteNames.of(SiteSource.Manual, "Beatenbucht"))
            }

        @Test
        fun `reads the take-off site of a Syride flight written before names had a source as Syride's`() =
            runTest {
                // given
                val flight = aStoredFlight().copy(importedFrom = ExternalReference("syride", "flight:3720061"))
                repository.save(flight)
                rewriteAsSchema(5, flight)

                // when
                val found = repository.findById(flight.id)

                // then
                assertThat(found?.details?.takeOffSite).isEqualTo(SiteNames.of(SiteSource.Syride, "Niederhorn"))
                assertThat(found?.details?.landingSite).isEqualTo(SiteNames.of(SiteSource.Manual, "Beatenbucht"))
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
        fun `restores the details and origin of a flight imported from another service`() =
            runTest {
                // given
                val base = aStoredFlight()
                val imported =
                    base.copy(
                        details =
                            base.details.copy(
                                distanceKm = 15.0,
                                topAltitudeMeters = 2464,
                                instrument = "Nav XL",
                                type = FlightType.HikeAndFly,
                            ),
                        importedFrom = ExternalReference("syride", "flight:3720061"),
                    )
                repository.save(imported)

                // when
                val found = repository.findById(imported.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(imported)
            }

        @Test
        fun `restores the services the flight was exported to`() =
            runTest {
                // given
                val exported = aStoredFlight().markExported("airtime", Instant.parse("2026-10-17T20:12:04Z"), "986")
                repository.save(exported)

                // when
                val found = repository.findById(exported.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(exported)
            }

        @Test
        fun `reads a flight written before exports were kept`() =
            runTest {
                // given
                val flight = aStoredFlight()
                repository.save(flight)
                rewriteAsSchema(4, flight, newerKeys = setOf("exports"))

                // when
                val found = repository.findById(flight.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(flight)
            }

        @Test
        fun `reads a flight written before take-off times were kept`() =
            runTest {
                // given
                val flight = aStoredFlight(takeOffTime = null)
                repository.save(flight)
                rewriteAsSchema(3, flight, newerKeys = setOf("takeOffTime"))

                // when
                val found = repository.findById(flight.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(flight)
            }

        @Test
        fun `reads a flight written before details could be imported`() =
            runTest {
                // given
                val flight = aStoredFlight(takeOffTime = null)
                repository.save(flight)
                val newerKeys =
                    setOf(
                        "distanceKm",
                        "topAltitudeMeters",
                        "instrument",
                        "importedFromSystem",
                        "importedFromId",
                        "takeOffTime",
                    )
                rewriteAsSchema(2, flight, newerKeys)

                // when
                val found = repository.findById(flight.id)

                // then
                assertThat(found).usingRecursiveComparison().isEqualTo(flight)
                assertThat(found?.importedFrom).isNull()
            }

        @Test
        fun `reads a flight written before tracks could be imported`() =
            runTest {
                // given
                val flight = aStoredFlight(takeOffTime = null)
                repository.save(flight)
                val newerKeys = setOf("igcAttachmentId", "igcFileName", "igcChecksum", "takeOffTime")
                rewriteAsSchema(1, flight, newerKeys)

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
                file.rewriteAsNewerVersion()

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
