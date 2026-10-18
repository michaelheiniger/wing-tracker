package ch.qscqlmpa.wingtracker.backup

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import ch.qscqlmpa.wingtracker.settings.ThemeMode
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
    private val settings = mockk<SettingsRepository>(relaxUnitFun = true)
    private val clock =
        AppClock(
            clock =
                object : Clock {
                    override fun now(): Instant = NOW
                },
            zone = TimeZone.of("Europe/Zurich"),
        )
    private val stored = AppSettings(themeMode = ThemeMode.Dark, syrideUsername = "michael.heiniger")

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
        private val getBackupStatus = GetBackupStatus(settings, clock)

        @Test
        fun `tells when the last backup was made in the pilot's time and offers a dated name`() =
            runTest {
                // given
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
                coEvery { settings.load() } returns stored

                // when
                val status = getBackupStatus()

                // then
                assertThat(status.lastMadeAt).isNull()
            }
    }
}
