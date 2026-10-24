package ch.qscqlmpa.wingtracker.ui.sync

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.settings.DeviceSettings
import ch.qscqlmpa.wingtracker.settings.DeviceSettingsRepository
import ch.qscqlmpa.wingtracker.sync.ConnectLogbook
import ch.qscqlmpa.wingtracker.sync.GetSyncStatus
import ch.qscqlmpa.wingtracker.sync.JoinLogbook
import ch.qscqlmpa.wingtracker.sync.LogbookSync
import ch.qscqlmpa.wingtracker.sync.RecordKind
import ch.qscqlmpa.wingtracker.sync.SaveAutomaticSync
import ch.qscqlmpa.wingtracker.sync.SaveSyncSettings
import ch.qscqlmpa.wingtracker.sync.SyncException
import ch.qscqlmpa.wingtracker.sync.SyncLogbook
import ch.qscqlmpa.wingtracker.sync.SyncProblem
import ch.qscqlmpa.wingtracker.sync.SyncReport
import ch.qscqlmpa.wingtracker.sync.SyncSettings
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val TOKEN = "ghp_exampletoken"

private val SETTINGS =
    SyncSettings(
        repository = "https://codeberg.org/michael/logbook.git",
        username = "michael",
        authorName = "Michael Heiniger",
        authorEmail = "michael@example.org",
    )

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSyncPresenterTest {
    private val sync =
        mockk<LogbookSync>(relaxUnitFun = true).also {
            coEvery { it.isConnected() } returns true
            coEvery { it.sync(any(), any()) } returns SyncReport(received = 2, sent = 1)
        }
    private var device = DeviceSettings(sync = SETTINGS)
    private val deviceSettings =
        mockk<DeviceSettingsRepository>().also {
            coEvery { it.load() } answers { device }
            coEvery { it.save(any()) } answers { device = firstArg() }
        }
    private val credentials =
        mockk<CredentialStore>(relaxUnitFun = true).also {
            coEvery { it.read(any()) } returns
                TOKEN
        }
    private var started: Boolean? = null
    private var exited = 0

    private fun TestScope.presenter(): AutoSyncPresenter {
        val clock = AppClock()
        return AutoSyncPresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            SyncUseCases(
                GetSyncStatus(sync, deviceSettings, clock),
                ConnectLogbook(sync, deviceSettings, clock),
                SaveSyncSettings(sync, deviceSettings),
                SyncLogbook(sync, deviceSettings, clock),
                JoinLogbook(sync, deviceSettings, clock),
                SaveAutomaticSync(deviceSettings),
            ),
            SyncSecret(credentials),
        ) { received -> started = received }
    }

    @Nested
    inner class StartUp {
        @Test
        fun `syncs, then shows the logbook and tells that other devices sent something`() =
            runTest {
                // given
                val presenter = presenter()

                // when
                presenter.startUp()

                // then
                coVerify(exactly = 1) { sync.sync(SETTINGS.remote(TOKEN), SETTINGS.author) }
                assertThat(presenter.start).isEqualTo(StartSync.Done)
                assertThat(started).isTrue()
            }

        @Test
        fun `shows the logbook at once when this device is not connected`() =
            runTest {
                // given
                device = DeviceSettings()
                val presenter = presenter()

                // when
                presenter.startUp()

                // then
                coVerify(exactly = 0) { sync.sync(any(), any()) }
                assertThat(presenter.start).isEqualTo(StartSync.Done)
                assertThat(started).isFalse()
            }

        @Test
        fun `does not sync when the pilot switched it off`() =
            runTest {
                // given
                device = device.copy(syncAtStartAndClose = false)
                val presenter = presenter()

                // when
                presenter.startUp()

                // then
                coVerify(exactly = 0) { sync.sync(any(), any()) }
                assertThat(presenter.start).isEqualTo(StartSync.Done)
            }

        @Test
        fun `goes on without a word when there is no connection`() =
            runTest {
                // given
                coEvery { sync.sync(any(), any()) } throws SyncException(SyncProblem.NoConnection)
                val presenter = presenter()

                // when
                presenter.startUp()

                // then
                assertThat(presenter.start).isEqualTo(StartSync.Done)
            }

        @Test
        fun `stops to name the records changed on two computers`() =
            runTest {
                // given
                val conflicts = listOf(SyncedRecord(RecordKind.Flight, "f-2026-09-20"))
                coEvery { sync.sync(any(), any()) } returns SyncReport(0, 0, conflicts)
                val presenter = presenter()

                // when
                presenter.startUp()

                // then
                assertThat(presenter.start).isEqualTo(StartSync.Stopped(SyncOutcome.Conflicts(conflicts), null))
                assertThat(started).isNull()
            }

        @Test
        fun `stops to tell when the host refused the token, then goes on when the pilot continues`() =
            runTest {
                // given
                coEvery { sync.sync(any(), any()) } throws SyncException(SyncProblem.CredentialsRefused)
                val presenter = presenter()
                presenter.startUp()
                val stopped = presenter.start as StartSync.Stopped

                // when
                presenter.continueAfterStop()

                // then
                assertThat(stopped.failure?.kind).isEqualTo(FailureKind.SyncCredentialsRefused)
                assertThat(presenter.start).isEqualTo(StartSync.Done)
                assertThat(started).isFalse()
            }

        @Test
        fun `shows the logbook at once when skipped`() =
            runTest {
                // given
                coEvery { sync.sync(any(), any()) } coAnswers { awaitCancellation() }
                val presenter = presenter()
                presenter.startUp()

                // when
                presenter.skip()

                // then
                assertThat(presenter.start).isEqualTo(StartSync.Done)
                assertThat(started).isFalse()
            }
    }

    @Nested
    inner class Close {
        @Test
        fun `syncs, then closes`() =
            runTest {
                // given
                val presenter = presenter()

                // when
                presenter.close { exited++ }

                // then
                coVerify(exactly = 1) { sync.sync(any(), any()) }
                assertThat(exited).isEqualTo(1)
            }

        @Test
        fun `closes even when the sync fails`() =
            runTest {
                // given
                coEvery { sync.sync(any(), any()) } throws SyncException(SyncProblem.Unreachable)
                val presenter = presenter()

                // when
                presenter.close { exited++ }

                // then
                assertThat(exited).isEqualTo(1)
            }

        @Test
        fun `closes at once without syncing when asked`() =
            runTest {
                // given
                coEvery { sync.sync(any(), any()) } coAnswers { awaitCancellation() }
                val presenter = presenter()
                presenter.close { exited++ }

                // when
                presenter.closeWithoutSync()

                // then
                assertThat(exited).isGreaterThanOrEqualTo(1)
            }
    }
}
