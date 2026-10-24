package ch.qscqlmpa.wingtracker.sync

import ch.qscqlmpa.wingtracker.flights.NOW
import ch.qscqlmpa.wingtracker.flights.aFixedClock
import ch.qscqlmpa.wingtracker.settings.DeviceSettings
import ch.qscqlmpa.wingtracker.settings.DeviceSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Instant

private const val TOKEN = "ghp_exampletoken"
private val LAST_SYNC = Instant.parse("2026-10-20T17:45:00Z")

private val SETTINGS =
    SyncSettings(
        repository = "https://codeberg.org/michael/logbook.git",
        username = "michael",
        authorName = "Michael Heiniger",
        authorEmail = "michael@example.org",
    )

class SyncUseCasesTest {
    private val sync = mockk<LogbookSync>(relaxUnitFun = true)
    private val deviceSettings = mockk<DeviceSettingsRepository>(relaxUnitFun = true)
    private val clock = aFixedClock()
    private val connectedDevice = DeviceSettings(interfaceScalePercent = 125, sync = SETTINGS, lastSyncedAt = LAST_SYNC)

    @Nested
    inner class GettingTheStatus {
        private val getSyncStatus = GetSyncStatus(sync, deviceSettings, clock)

        @Test
        fun `shows the repository and the last sync in the pilot's time`() =
            runTest {
                // given
                coEvery { deviceSettings.load() } returns connectedDevice
                coEvery { sync.isConnected() } returns true

                // when
                val status = getSyncStatus()

                // then
                assertThat(
                    status,
                ).isEqualTo(SyncStatus(SETTINGS, LocalDateTime(2026, 10, 20, 17, 45), automatic = true))
            }

        @Test
        fun `does not sync at start and close when the pilot switched it off`() =
            runTest {
                // given
                coEvery { deviceSettings.load() } returns connectedDevice.copy(syncAtStartAndClose = false)
                coEvery { sync.isConnected() } returns true

                // when
                val status = getSyncStatus()

                // then
                assertThat(status.automatic).isFalse()
            }

        @Test
        fun `is not connected when the data folder holds no synchronised logbook, for example after changing it`() =
            runTest {
                // given
                coEvery { deviceSettings.load() } returns connectedDevice
                coEvery { sync.isConnected() } returns false

                // when
                val status = getSyncStatus()

                // then
                assertThat(status).isEqualTo(SyncStatus(settings = null, lastSyncedAt = null))
            }
    }

    @Nested
    inner class Connecting {
        @Test
        fun `connects, then remembers the repository and the time, keeping the other device settings`() =
            runTest {
                // given
                coEvery { deviceSettings.load() } returns DeviceSettings(interfaceScalePercent = 125)

                // when
                ConnectLogbook(sync, deviceSettings, clock)(SETTINGS, TOKEN)

                // then
                coVerify(exactly = 1) { sync.connect(SETTINGS.remote(TOKEN), SETTINGS.author) }
                coVerify(exactly = 1) {
                    deviceSettings.save(
                        DeviceSettings(interfaceScalePercent = 125, sync = SETTINGS, lastSyncedAt = NOW),
                    )
                }
            }

        @Test
        fun `remembers nothing when the repository refused`() =
            runTest {
                // given
                coEvery { sync.connect(any(), any()) } throws SyncException(SyncProblem.RemoteNotEmpty)

                // when
                val failure =
                    runCatching {
                        ConnectLogbook(
                            sync,
                            deviceSettings,
                            clock,
                        )(SETTINGS, TOKEN)
                    }.exceptionOrNull()

                // then
                assertThat((failure as? SyncException)?.problem).isEqualTo(SyncProblem.RemoteNotEmpty)
                coVerify(exactly = 0) { deviceSettings.save(any()) }
            }
    }

    @Nested
    inner class Joining {
        @Test
        fun `copies the logbook, then gives the new folder this device's settings without the old logbook's backup`() =
            runTest {
                // given
                val folder = "/home/michael/Flugbuch"
                coEvery { deviceSettings.load() } returns
                    DeviceSettings(interfaceScalePercent = 125, lastBackupAt = Instant.parse("2026-10-18T12:30:00Z"))

                // when
                JoinLogbook(sync, deviceSettings, clock)(SETTINGS, TOKEN, folder)

                // then
                coVerify(exactly = 1) { sync.join(SETTINGS.remote(TOKEN), SETTINGS.author, folder) }
                coVerify(exactly = 1) {
                    deviceSettings.saveFor(
                        folder,
                        DeviceSettings(interfaceScalePercent = 125, sync = SETTINGS, lastSyncedAt = NOW),
                    )
                }
                coVerify(exactly = 0) { deviceSettings.save(any()) }
            }
    }

    @Nested
    inner class Synchronising {
        private val syncLogbook = SyncLogbook(sync, deviceSettings, clock)

        @Test
        fun `exchanges the changes and remembers when`() =
            runTest {
                // given
                coEvery { deviceSettings.load() } returns connectedDevice
                coEvery { sync.sync(SETTINGS.remote(TOKEN), SETTINGS.author) } returns
                    SyncReport(received = 3, sent = 1)

                // when
                val report = syncLogbook(TOKEN)

                // then
                assertThat(report).isEqualTo(SyncReport(received = 3, sent = 1))
                coVerify(exactly = 1) { deviceSettings.save(connectedDevice.copy(lastSyncedAt = NOW)) }
            }

        @Test
        fun `passes on which version of each conflicting record to keep`() =
            runTest {
                // given
                val record = SyncedRecord(RecordKind.Flight, "f-2026-09-20")
                coEvery { deviceSettings.load() } returns connectedDevice
                coEvery { sync.sync(any(), any(), any()) } returns SyncReport(received = 2, sent = 2)

                // when
                syncLogbook(TOKEN, mapOf(record to ConflictChoice.There))

                // then
                coVerify(exactly = 1) {
                    sync.sync(SETTINGS.remote(TOKEN), SETTINGS.author, mapOf(record to ConflictChoice.There))
                }
            }

        @Test
        fun `keeps the last sync when records changed on both sides stopped it`() =
            runTest {
                // given
                coEvery { deviceSettings.load() } returns connectedDevice
                coEvery { sync.sync(any(), any()) } returns
                    SyncReport(0, 0, listOf(SyncedRecord(RecordKind.Flight, "f-2026-09-20")))

                // when
                syncLogbook(TOKEN)

                // then
                coVerify(exactly = 0) { deviceSettings.save(any()) }
            }
    }

    @Nested
    inner class SavingTheSettings {
        @Test
        fun `signs the next changes with the new author and keeps the repository`() =
            runTest {
                // given
                coEvery { deviceSettings.load() } returns connectedDevice

                // when
                SaveSyncSettings(sync, deviceSettings)(null, "Michael H.", "michael@example.ch")

                // then
                val changed =
                    SETTINGS.copy(
                        username = null,
                        authorName = "Michael H.",
                        authorEmail = "michael@example.ch",
                    )
                coVerify(exactly = 1) { sync.changeAuthor(SyncAuthor("Michael H.", "michael@example.ch")) }
                coVerify(exactly = 1) { deviceSettings.save(connectedDevice.copy(sync = changed)) }
            }
    }

    @Nested
    inner class SavingTheAutomaticSync {
        @Test
        fun `switches syncing at start and close off and keeps the other device settings`() =
            runTest {
                // given
                coEvery { deviceSettings.load() } returns connectedDevice

                // when
                SaveAutomaticSync(deviceSettings)(false)

                // then
                coVerify(exactly = 1) { deviceSettings.save(connectedDevice.copy(syncAtStartAndClose = false)) }
            }
    }

    @Nested
    inner class SettingsRules {
        @Test
        fun `accepts only an HTTPS repository`() {
            assertThatThrownBy { SETTINGS.copy(repository = "http://codeberg.org/michael/logbook.git") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `needs a name and an email address to sign the changes`() {
            assertThatThrownBy { SETTINGS.copy(authorName = " ") }.isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy { SETTINGS.copy(authorEmail = "") }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
