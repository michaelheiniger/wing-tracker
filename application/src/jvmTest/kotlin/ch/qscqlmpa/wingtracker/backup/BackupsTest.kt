package ch.qscqlmpa.wingtracker.backup

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.settings.DeviceSettings
import ch.qscqlmpa.wingtracker.settings.DeviceSettingsRepository
import ch.qscqlmpa.wingtracker.settings.LogbookSettings
import ch.qscqlmpa.wingtracker.settings.LogbookSettingsRepository
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.RecordingTransactions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

private const val APP_VERSION = "0.4.0"
private const val DESTINATION = "/media/michael/BACKUP/wing-tracker-backup-2026-10-18.zip"
private val NOW = Instant.parse("2026-10-18T12:30:00Z")
private val EARLIER_BACKUP = Instant.parse("2026-09-27T17:05:00Z")

class BackupsTest {
    private val archive = mockk<BackupArchive>()
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
    private val stored = DeviceSettings(interfaceScalePercent = 125, deviceFolder = "/run/media/michael/SYRIDE1")

    @Nested
    inner class CreateBackupInvoke {
        private val createBackup = CreateBackup(archive, settings, clock, APP_VERSION)

        @Test
        fun `archives the data and remembers when, keeping the other settings`() =
            runTest {
                // given
                val summary = BackupSummary(files = 412, bytes = 18_350_080)
                coEvery { archive.create(DESTINATION, APP_VERSION, NOW) } returns summary
                coEvery { settings.load() } returns stored

                // when
                val result = createBackup(DESTINATION)

                // then
                assertThat(result).isEqualTo(summary)
                coVerify(exactly = 1) { settings.save(stored.copy(lastBackupAt = NOW)) }
            }

        @Test
        fun `does not remember a backup that could not be made`() =
            runTest {
                // given
                coEvery { archive.create(DESTINATION, APP_VERSION, NOW) } throws
                    BackupException(BackupProblem.InsideDataFolder, DESTINATION)

                // when
                val failure = runCatching { createBackup(DESTINATION) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(BackupException::class.java)
                coVerify(exactly = 0) { settings.save(any()) }
            }
    }

    @Nested
    inner class GetBackupStatusInvoke {
        private val getBackupStatus = GetBackupStatus(logbookSettings, settings, clock)

        @Test
        fun `tells when the last backup was made in the pilot's time and offers a dated name`() =
            runTest {
                // given
                coEvery { logbookSettings.load() } returns LogbookSettings()
                coEvery { settings.load() } returns stored.copy(lastBackupAt = EARLIER_BACKUP)

                // when
                val status = getBackupStatus()

                // then
                assertThat(status)
                    .isEqualTo(
                        BackupStatus(
                            lastMadeAt = LocalDateTime(2026, 9, 27, 19, 5),
                            suggestedFileName = "wing-tracker-backup-2026-10-18.zip",
                        ),
                    )
            }

        @Test
        fun `has no last backup when none was made yet`() =
            runTest {
                // given
                coEvery { logbookSettings.load() } returns LogbookSettings()
                coEvery { settings.load() } returns stored

                // when
                val status = getBackupStatus()

                // then
                assertThat(status.lastMadeAt).isNull()
            }
    }

    @Nested
    inner class SaveCloudStorageInvoke {
        private val settings = mockk<LogbookSettingsRepository>(relaxUnitFun = true)
        private val transactions = RecordingTransactions()
        private val bucket =
            CloudStorageSettings(
                provider = CloudProvider.MegaS4,
                region = "eu-amsterdam-1",
                endpoint = null,
                bucket = "wing-tracker-backups",
                accessKeyId = "AKIAMEGAS4EXAMPLE",
                keepLast = 10,
            )

        @Test
        fun `remembers the bucket and keeps the other logbook settings`() =
            runTest {
                // given
                coEvery { settings.load() } returns LogbookSettings(syrideUsername = "michael.heiniger")

                // when
                SaveCloudStorage(settings, transactions)(bucket)

                // then
                coVerify(exactly = 1) {
                    settings.save(LogbookSettings(syrideUsername = "michael.heiniger", cloudStorage = bucket))
                }
            }

        @Test
        fun `records the bucket as one change to the logbook`() =
            runTest {
                // given
                coEvery { settings.load() } returns LogbookSettings()

                // when
                SaveCloudStorage(settings, transactions)(bucket)

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.CloudStorageChanged)
            }
    }

    @Nested
    inner class InspectBackupInvoke {
        private val contents =
            BackupContents(
                createdAt = EARLIER_BACKUP,
                appVersion = APP_VERSION,
                summary = BackupSummary(files = 412, bytes = 18_350_080),
            )

        @Test
        fun `shows a checked backup in the pilot's time`() =
            runTest {
                // given
                coEvery { archive.inspect(DESTINATION) } returns contents

                // when
                val preview = InspectBackup(archive, clock, APP_VERSION)(DESTINATION)

                // then
                assertThat(preview)
                    .isEqualTo(
                        BackupPreview(
                            madeAt = LocalDateTime(2026, 9, 27, 19, 5),
                            appVersion = APP_VERSION,
                            summary = contents.summary,
                            madeByNewerVersion = false,
                        ),
                    )
            }

        @Test
        fun `tells when the backup was made by a newer version`() =
            runTest {
                // given
                coEvery { archive.inspect(DESTINATION) } returns contents.copy(appVersion = "0.5.0")

                // when
                val preview = InspectBackup(archive, clock, APP_VERSION)(DESTINATION)

                // then
                assertThat(preview.madeByNewerVersion).isTrue()
            }

        @Test
        fun `cannot tell a newer backup on a development build`() =
            runTest {
                // given
                coEvery { archive.inspect(DESTINATION) } returns contents.copy(appVersion = "0.5.0")

                // when
                val preview = InspectBackup(archive, clock, "dev")(DESTINATION)

                // then
                assertThat(preview.madeByNewerVersion).isFalse()
            }
    }
}
