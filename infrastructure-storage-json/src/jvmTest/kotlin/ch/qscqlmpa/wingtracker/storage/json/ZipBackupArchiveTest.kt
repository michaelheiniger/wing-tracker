package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.backup.BackupException
import ch.qscqlmpa.wingtracker.backup.BackupProblem
import ch.qscqlmpa.wingtracker.backup.BackupSummary
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.time.Instant

private const val APP_VERSION = "0.4.0"
private val CREATED_AT = Instant.parse("2026-10-18T12:30:00Z")

private const val WING = "wings/5b0c7f0e-3c1a-4c55-9a51-6c0f2b0a9e11.json"
private const val FLIGHT = "flights/0d6a3c52-8b7e-4f0e-a2a4-1d9b0f6e7c33.json"
private const val TRACK = "attachments/9f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b"
private const val QUARANTINED = "quarantine/flights/7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d.json"
private const val SETTINGS = "settings.json"

class ZipBackupArchiveTest {
    @TempDir
    lateinit var dataDirectory: Path

    @TempDir
    lateinit var backupDirectory: Path

    private val archive by lazy { ZipBackupArchive(dataDirectory) }
    private val track = "AXSYRIDE\r\nHFDTE181026\r\nB1230004612345N00712345EA0150001600\r\n".encodeToByteArray()

    @BeforeEach
    fun storeData() {
        dataDirectory.store(WING, """{"schemaVersion": 3, "brand": "Ozone", "model": "Rush 6", "size": "MS"}""")
        dataDirectory.store(FLIGHT, """{"schemaVersion": 5, "date": "2026-10-11", "takeOffSite": "Moléson"}""")
        dataDirectory.store(QUARANTINED, "{not json")
        dataDirectory.store(SETTINGS, """{"schemaVersion": 7, "themeMode": "Dark"}""")
        dataDirectory.resolve(TRACK).also { it.parent.createDirectories() }.writeBytes(track)
    }

    @Nested
    inner class Create {
        @Test
        fun `archives every stored file as it is, the quarantine included`() =
            runTest {
                // given
                val destination = backupDirectory.resolve("wing-tracker-backup-2026-10-18.zip")

                // when
                archive.create(destination.toString(), APP_VERSION, CREATED_AT)

                // then
                ZipFile(destination.toFile()).use { zip ->
                    val names = zip.entries().toList().map { it.name }
                    assertThat(names).containsExactlyInAnyOrder(
                        WING,
                        FLIGHT,
                        TRACK,
                        QUARANTINED,
                        SETTINGS,
                        BackupManifestDocument.ENTRY_NAME,
                    )
                    assertThat(zip.getInputStream(zip.getEntry(TRACK)).readBytes()).isEqualTo(track)
                }
            }

        @Test
        fun `lists each file with its size and checksum in a manifest`() =
            runTest {
                // given
                val destination = backupDirectory.resolve("wing-tracker-backup-2026-10-18.zip")

                // when
                archive.create(destination.toString(), APP_VERSION, CREATED_AT)

                // then
                val manifest = destination.manifest()
                assertThat(manifest.formatVersion).isEqualTo(BackupManifestDocument.CURRENT_FORMAT_VERSION)
                assertThat(manifest.appVersion).isEqualTo(APP_VERSION)
                assertThat(manifest.createdAt).isEqualTo("2026-10-18T12:30:00Z")
                assertThat(manifest.files.single { it.path == TRACK })
                    .isEqualTo(BackupEntryDocument(TRACK, track.size.toLong(), sha256(track)))
            }

        @Test
        fun `tells how many files and bytes went into the backup`() =
            runTest {
                // given
                val destination = backupDirectory.resolve("wing-tracker-backup-2026-10-18.zip")
                val expectedBytes =
                    Files
                        .walk(
                            dataDirectory,
                        ).filter { Files.isRegularFile(it) }
                        .mapToLong(Files::size)
                        .sum()

                // when
                val summary = archive.create(destination.toString(), APP_VERSION, CREATED_AT)

                // then
                assertThat(summary).isEqualTo(BackupSummary(files = 5, bytes = expectedBytes))
            }

        @Test
        fun `leaves out files that are still being written`() =
            runTest {
                // given
                dataDirectory.store("flights/0d6a3c52-8b7e-4f0e-a2a4-1d9b0f6e7c33-4812.tmp", """{"schemaVersion": 5""")
                val destination = backupDirectory.resolve("wing-tracker-backup-2026-10-18.zip")

                // when
                archive.create(destination.toString(), APP_VERSION, CREATED_AT)

                // then
                assertThat(destination.manifest().files.map { it.path }).noneMatch { it.endsWith(".tmp") }
            }

        @Test
        fun `replaces an earlier backup of the same name and leaves no temporary file behind`() =
            runTest {
                // given
                val destination = backupDirectory.resolve("wing-tracker-backup-2026-10-18.zip")
                destination.writeText("an older backup")

                // when
                archive.create(destination.toString(), APP_VERSION, CREATED_AT)

                // then
                assertThat(destination.manifest().files).hasSize(5)
                assertThat(backupDirectory.listDirectoryEntries()).containsExactly(destination)
            }

        @Test
        fun `refuses to save the backup inside the data folder`() =
            runTest {
                // given
                val destination = dataDirectory.resolve("backups/wing-tracker-backup-2026-10-18.zip")

                // when
                val failure = runCatching { archive.create(destination.toString(), APP_VERSION, CREATED_AT) }

                // then
                assertThat(failure.exceptionOrNull())
                    .isInstanceOfSatisfying(BackupException::class.java) {
                        assertThat(it.problem).isEqualTo(BackupProblem.InsideDataFolder)
                    }
                assertThat(Files.exists(destination.parent)).isFalse()
            }

        // Creating a link needs administrator rights or developer mode on Windows.
        @DisabledOnOs(OS.WINDOWS)
        @Test
        fun `refuses a folder that links into the data folder`() =
            runTest {
                // given
                val link = Files.createSymbolicLink(backupDirectory.resolve("logbook"), dataDirectory)
                val destination = link.resolve("wing-tracker-backup-2026-10-18.zip")

                // when
                val failure = runCatching { archive.create(destination.toString(), APP_VERSION, CREATED_AT) }

                // then
                assertThat((failure.exceptionOrNull() as? BackupException)?.problem)
                    .isEqualTo(BackupProblem.InsideDataFolder)
            }
    }

    private fun Path.store(
        path: String,
        text: String,
    ) {
        resolve(path).also { it.parent.createDirectories() }.writeText(text)
    }

    private fun Path.manifest(): BackupManifestDocument =
        ZipFile(toFile()).use { zip ->
            val text = zip.getInputStream(zip.getEntry(BackupManifestDocument.ENTRY_NAME)).readBytes().decodeToString()
            json.decodeFromString<BackupManifestDocument>(text)
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
