package ch.qscqlmpa.wingtracker.backup

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.settings.LogbookSettings
import ch.qscqlmpa.wingtracker.settings.LogbookSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private const val SECRET = "s3cr3t/K3y"
private const val NEWEST = "wing-tracker/wing-tracker-backup-2026-10-18-1430.zip"
private const val OLDER = "wing-tracker/wing-tracker-backup-2026-09-27-1905.zip"
private const val TEMPORARY = "/tmp/wing-tracker-backup-81273/wing-tracker-backup-2026-10-18-1430.zip"

private val BUCKET =
    CloudStorageSettings(
        provider = CloudProvider.MegaS4,
        region = "eu-amsterdam-1",
        endpoint = null,
        bucket = "wing-tracker-backups",
        accessKeyId = "AKIAMEGAS4EXAMPLE",
        keepLast = 10,
    )
private val TARGET = BUCKET.target(SECRET)

@OptIn(ExperimentalCoroutinesApi::class)
class CloudRestoreTest {
    private val storage = mockk<CloudStorage>(relaxUnitFun = true)
    private val archive = mockk<BackupArchive>(relaxUnitFun = true)
    private val settings =
        mockk<LogbookSettingsRepository>().also {
            coEvery { it.load() } returns
                LogbookSettings(cloudStorage = BUCKET)
        }
    private val clock = AppClock(zone = TimeZone.of("Europe/Zurich"))

    @Nested
    inner class ListCloudBackupsInvoke {
        @Test
        fun `lists only the application's backups, newest first, in the pilot's time`() =
            runTest {
                // given
                coEvery { storage.list(TARGET, "wing-tracker/") } returns
                    listOf(
                        CloudObject(OLDER, 17_900_000, Instant.parse("2026-09-27T17:05:30Z")),
                        CloudObject("wing-tracker/notes.txt", 120, Instant.parse("2026-10-20T08:00:00Z")),
                        CloudObject(NEWEST, 18_350_080, Instant.parse("2026-10-18T12:30:20Z")),
                    )

                // when
                val backups = ListCloudBackups(storage, settings, clock)(SECRET)

                // then
                assertThat(backups).containsExactly(
                    CloudBackup(NEWEST, LocalDateTime(2026, 10, 18, 14, 30, 20), 18_350_080),
                    CloudBackup(OLDER, LocalDateTime(2026, 9, 27, 19, 5, 30), 17_900_000),
                )
            }
    }

    @Nested
    inner class DownloadCloudBackupInvoke {
        @Test
        fun `downloads the backup into a temporary file`() =
            runTest {
                // given
                coEvery { archive.temporaryFile("wing-tracker-backup-2026-10-18-1430.zip") } returns TEMPORARY

                // when
                val file = DownloadCloudBackup(storage, archive, settings)(SECRET, NEWEST)

                // then
                assertThat(file).isEqualTo(TEMPORARY)
                coVerify(exactly = 1) { storage.download(TARGET, NEWEST, TEMPORARY) }
                coVerify(exactly = 0) { archive.discard(any()) }
            }

        @Test
        fun `leaves nothing behind when the download fails`() =
            runTest {
                // given
                coEvery { archive.temporaryFile(any()) } returns TEMPORARY
                coEvery { storage.download(any(), any(), any()) } throws CloudStorageException(CloudProblem.Unreachable)

                // when
                val failure =
                    runCatching {
                        DownloadCloudBackup(
                            storage,
                            archive,
                            settings,
                        )(SECRET, NEWEST)
                    }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(CloudStorageException::class.java)
                coVerify(exactly = 1) { archive.discard(TEMPORARY) }
            }

        @Test
        fun `leaves nothing behind when the download is cancelled`() =
            runTest {
                // given
                coEvery { archive.temporaryFile(any()) } returns TEMPORARY
                coEvery { storage.download(any(), any(), any()) } coAnswers { awaitCancellation() }
                val running = launch { DownloadCloudBackup(storage, archive, settings)(SECRET, NEWEST) }
                runCurrent()

                // when
                running.cancelAndJoin()

                // then
                coVerify(exactly = 1) { archive.discard(TEMPORARY) }
            }

        @Test
        fun `refuses an object that is no backup of the application`() =
            runTest {
                // when
                val failure =
                    runCatching { DownloadCloudBackup(storage, archive, settings)(SECRET, "wing-tracker/notes.txt") }
                        .exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
                coVerify(exactly = 0) { storage.download(any(), any(), any()) }
            }
    }
}
