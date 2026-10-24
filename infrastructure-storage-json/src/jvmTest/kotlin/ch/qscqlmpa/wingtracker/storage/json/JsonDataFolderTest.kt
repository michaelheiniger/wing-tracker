package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.sync.RecordKind
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
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
