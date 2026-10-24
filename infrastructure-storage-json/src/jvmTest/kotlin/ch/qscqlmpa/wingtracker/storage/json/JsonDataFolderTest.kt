package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.sync.FieldDifference
import ch.qscqlmpa.wingtracker.sync.RecordKind
import ch.qscqlmpa.wingtracker.sync.RecordSummary
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

class JsonDataFolderTest {
    @TempDir
    lateinit var dataDirectory: Path

    @Nested
    inner class IsQuarantined {
        @Test
        fun `tells a record the repository set aside`() =
            runTest {
                // given
                val flights = JsonFlightRepository(dataDirectory)
                dataDirectory.resolve("flights").toFile().mkdirs()
                dataDirectory.resolve("flights/f-1.json").writeText("not json at all")
                flights.findAll()

                // when
                val quarantined = JsonDataFolder.isQuarantined(dataDirectory, "flights/f-1.json")

                // then
                assertThat(dataDirectory.resolve("quarantine/flights/f-1.json").exists()).isTrue()
                assertThat(quarantined).isTrue()
            }

        @Test
        fun `tells a record set aside again while an earlier copy was there`() {
            // given
            dataDirectory.resolve("quarantine/flights").toFile().mkdirs()
            dataDirectory.resolve("quarantine/flights/f-1-2.json").writeText("not json at all")

            // when
            val quarantined = JsonDataFolder.isQuarantined(dataDirectory, "flights/f-1.json")

            // then
            assertThat(quarantined).isTrue()
        }

        @Test
        fun `does not take a deleted record for a quarantined one`() {
            // given
            dataDirectory.resolve("quarantine/flights").toFile().mkdirs()
            dataDirectory.resolve("quarantine/flights/f-10.json").writeText("not json at all")

            // when
            val quarantined = JsonDataFolder.isQuarantined(dataDirectory, "flights/f-1.json")

            // then
            assertThat(quarantined).isFalse()
        }
    }

    @Nested
    inner class RecordAt {
        @Test
        fun `names the record each stored file holds`() {
            // given
            val paths = listOf("wings/w-1.json", "flights/f-1.json", "attachments/a-1", "settings.json", ".gitignore")

            // when
            val records = paths.map(JsonDataFolder::recordAt)

            // then
            assertThat(records).containsExactly(
                SyncedRecord(RecordKind.Wing, "w-1"),
                SyncedRecord(RecordKind.Flight, "f-1"),
                SyncedRecord(RecordKind.Attachment, "a-1"),
                SyncedRecord(RecordKind.LogbookSettings, "settings"),
                SyncedRecord(RecordKind.Other, ".gitignore"),
            )
        }
    }

    @Nested
    inner class Summary {
        @Test
        fun `names a piece of gear by its name`() =
            runTest {
                // given
                val wing = aStoredWing()
                JsonWingRepository(dataDirectory).save(wing)
                val stored = dataDirectory.resolve("wings/${wing.id.value}.json").readBytes()

                // when
                val summary = JsonDataFolder.summary("wings/${wing.id.value}.json", stored)

                // then
                assertThat(summary).isEqualTo(RecordSummary.Gear("Sky Dancer"))
            }

        @Test
        fun `names a flight by its date and take-off site`() =
            runTest {
                // given
                val flight = aStoredFlight()
                JsonFlightRepository(dataDirectory).save(flight)
                val stored = dataDirectory.resolve("flights/${flight.id.value}.json").readBytes()

                // when
                val summary = JsonDataFolder.summary("flights/${flight.id.value}.json", stored)

                // then
                assertThat(summary).isEqualTo(RecordSummary.Flight(LocalDate(2026, 9, 20), "Niederhorn"))
            }

        @Test
        fun `has no name for a version it cannot read`() {
            // given / when
            val summary = JsonDataFolder.summary("flights/f-1.json", "not json at all".encodeToByteArray())

            // then
            assertThat(summary).isNull()
        }
    }

    @Nested
    inner class Differences {
        private val here =
            """{"schemaVersion": 5, "date": "2026-09-20", "durationMinutes": 95, "sites": ["Niederhorn"]}"""
        private val there =
            """{"schemaVersion": 6, "date": "2026-09-20", "durationMinutes": 120, "sites": ["Niederhorn", "Beatenbucht"]}"""

        @Test
        fun `names each field that differs, nested ones by their path, leaving out the format version`() {
            // given / when
            val differences =
                JsonDataFolder.differences("flights/f-1.json", here.encodeToByteArray(), there.encodeToByteArray())

            // then
            assertThat(differences).containsExactly(
                FieldDifference("durationMinutes", "95", "120"),
                FieldDifference("sites[1]", null, "Beatenbucht"),
            )
        }

        @Test
        fun `shows every field of a version the other side deleted`() {
            // given / when
            val differences = JsonDataFolder.differences("flights/f-1.json", here.encodeToByteArray(), null)

            // then
            assertThat(differences.map { it.field }).containsExactly("date", "durationMinutes", "sites[0]")
            assertThat(differences.map { it.there }).containsOnlyNulls()
        }

        @Test
        fun `has no fields to compare for an attachment`() {
            // given / when
            val differences = JsonDataFolder.differences("attachments/a-1", byteArrayOf(1), byteArrayOf(2))

            // then
            assertThat(differences).isEmpty()
        }
    }

    @Nested
    inner class DeviceOnly {
        @Test
        fun `keeps the device settings, the quarantine and files being written on the device`() {
            // given / when
            val patterns = JsonDataFolder.deviceOnly

            // then
            assertThat(patterns).containsExactlyInAnyOrder("/device-settings.json", "/quarantine/", "*.tmp")
        }
    }
}
