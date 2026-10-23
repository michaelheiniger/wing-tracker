package ch.qscqlmpa.wingtracker.backup

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.settings.DeviceSettings
import ch.qscqlmpa.wingtracker.settings.DeviceSettingsRepository
import ch.qscqlmpa.wingtracker.settings.LogbookSettings
import ch.qscqlmpa.wingtracker.settings.LogbookSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

private const val APP_VERSION = "0.4.0"
private const val SECRET = "s3cr3t/K3y"
private const val TEMPORARY = "/tmp/wing-tracker-backup-81273/wing-tracker-backup-2026-10-18-1430.zip"
private const val KEY = "wing-tracker/wing-tracker-backup-2026-10-18-1430.zip"
private val NOW = Instant.parse("2026-10-18T12:30:00Z")
private val SUMMARY = BackupSummary(files = 412, bytes = 18_350_080)

private val BUCKET =
    CloudStorageSettings(
        provider = CloudProvider.MegaS4,
        region = "eu-amsterdam-1",
        endpoint = null,
        bucket = "wing-tracker-backups",
        accessKeyId = "AKIAMEGAS4EXAMPLE",
        keepLast = 2,
    )
private val TARGET = BUCKET.target(SECRET)

private fun uploaded(
    key: String,
    at: String,
) = CloudObject(key, 18_000_000, Instant.parse(at))

@OptIn(ExperimentalCoroutinesApi::class)
class UploadBackupTest {
    private val archive = mockk<BackupArchive>(relaxUnitFun = true)
    private val storage = mockk<CloudStorage>(relaxUnitFun = true)
    private val logbookSettings = mockk<LogbookSettingsRepository>()
    private val settings = mockk<DeviceSettingsRepository>(relaxUnitFun = true)
    private val clock =
        AppClock(
            clock =
                object : Clock {
                    override fun now(): Instant = NOW
                },
            zone = TimeZone.of("Europe/Zurich"),
        )
    private val uploadBackup = UploadBackup(archive, storage, logbookSettings, settings, clock, APP_VERSION)

    private fun givenBucket(cloud: CloudStorageSettings = BUCKET) {
        coEvery { logbookSettings.load() } returns LogbookSettings(cloudStorage = cloud)
        coEvery { settings.load() } returns DeviceSettings(interfaceScalePercent = 125)
        coEvery { archive.temporaryFile("wing-tracker-backup-2026-10-18-1430.zip") } returns TEMPORARY
        coEvery { archive.create(TEMPORARY, APP_VERSION, NOW) } returns SUMMARY
    }

    @Nested
    inner class Invoke {
        @Test
        fun `uploads the backup under a name dated in the pilot's time, then removes the local copy`() =
            runTest {
                // given
                givenBucket(BUCKET.copy(keepLast = null))

                // when
                val result = uploadBackup(SECRET)

                // then
                assertThat(result).isEqualTo(UploadedBackup(BUCKET.bucket, KEY, SUMMARY, 0, null))
                coVerifyOrder {
                    archive.create(TEMPORARY, APP_VERSION, NOW)
                    storage.upload(TARGET, KEY, TEMPORARY)
                    archive.discard(TEMPORARY)
                }
                coVerify(exactly = 1) { settings.save(DeviceSettings(interfaceScalePercent = 125, lastBackupAt = NOW)) }
                coVerify(exactly = 0) { storage.list(any(), any()) }
            }

        @Test
        fun `removes the local copy and remembers no backup when the upload fails`() =
            runTest {
                // given
                givenBucket()
                coEvery { storage.upload(any(), any(), any()) } throws CloudStorageException(CloudProblem.Unreachable)

                // when
                val failure = runCatching { uploadBackup(SECRET) }.exceptionOrNull()

                // then
                assertThat((failure as? CloudStorageException)?.problem).isEqualTo(CloudProblem.Unreachable)
                coVerify(exactly = 1) { archive.discard(TEMPORARY) }
                coVerify(exactly = 0) { settings.save(any()) }
            }

        @Test
        fun `deletes the oldest of its own backups beyond the number to keep, and nothing else`() =
            runTest {
                // given
                givenBucket()
                coEvery { storage.list(TARGET, "wing-tracker/") } returns
                    listOf(
                        uploaded("wing-tracker/wing-tracker-backup-2026-09-27-1905.zip", "2026-09-27T17:05:30Z"),
                        uploaded(KEY, "2026-10-18T12:30:20Z"),
                        uploaded("wing-tracker/wing-tracker-backup-2026-08-30-0815.zip", "2026-08-30T06:15:30Z"),
                        uploaded("wing-tracker/wing-tracker-backup-2026-10-11-2010.zip", "2026-10-11T18:10:30Z"),
                        uploaded("wing-tracker/notes.txt", "2026-01-01T00:00:00Z"),
                    )

                // when
                val result = uploadBackup(SECRET)

                // then
                assertThat(result.deleted).isEqualTo(2)
                coVerify(exactly = 1) { storage.delete(TARGET, "wing-tracker/wing-tracker-backup-2026-09-27-1905.zip") }
                coVerify(exactly = 1) { storage.delete(TARGET, "wing-tracker/wing-tracker-backup-2026-08-30-0815.zip") }
                coVerify(exactly = 0) { storage.delete(TARGET, "wing-tracker/notes.txt") }
            }

        @Test
        fun `still reports the upload when deleting older backups is refused`() =
            runTest {
                // given
                givenBucket()
                coEvery { storage.list(any(), any()) } returns
                    listOf(
                        uploaded(KEY, "2026-10-18T12:30:20Z"),
                        uploaded("wing-tracker/wing-tracker-backup-2026-10-11-2010.zip", "2026-10-11T18:10:30Z"),
                        uploaded("wing-tracker/wing-tracker-backup-2026-09-27-1905.zip", "2026-09-27T17:05:30Z"),
                    )
                coEvery { storage.delete(any(), any()) } throws CloudStorageException(CloudProblem.AccessDenied)

                // when
                val result = uploadBackup(SECRET)

                // then
                assertThat(result).isEqualTo(UploadedBackup(BUCKET.bucket, KEY, SUMMARY, 0, CloudProblem.AccessDenied))
                coVerify(exactly = 1) { settings.save(match { it.lastBackupAt == NOW }) }
            }
    }

    @Nested
    inner class Cancelling {
        @Test
        fun `removes the local copy and records nothing when cancelled during the upload`() =
            runTest {
                // given
                givenBucket()
                coEvery { storage.upload(any(), any(), any()) } coAnswers { awaitCancellation() }
                val running = launch { uploadBackup(SECRET) }
                runCurrent()

                // when
                running.cancelAndJoin()

                // then
                coVerify(exactly = 1) { archive.discard(TEMPORARY) }
                coVerify(exactly = 0) { settings.save(any()) }
            }

        @Test
        fun `still records and tidies up when cancelled once the backup is in the bucket`() =
            runTest {
                // given
                givenBucket()
                val older = "wing-tracker/wing-tracker-backup-2026-08-30-0815.zip"
                coEvery { storage.list(any(), any()) } coAnswers {
                    delay(1_000)
                    listOf(
                        uploaded(KEY, "2026-10-18T12:30:20Z"),
                        uploaded("wing-tracker/wing-tracker-backup-2026-10-11-2010.zip", "2026-10-11T18:10:30Z"),
                        uploaded(older, "2026-08-30T06:15:30Z"),
                    )
                }
                val running = launch { uploadBackup(SECRET) }
                runCurrent()

                // when
                running.cancelAndJoin()

                // then
                coVerify(exactly = 1) { settings.save(match { it.lastBackupAt == NOW }) }
                coVerify(exactly = 1) { storage.delete(TARGET, older) }
            }
    }
}
