package ch.qscqlmpa.wingtracker.ui.sync

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.settings.DeviceSettings
import ch.qscqlmpa.wingtracker.settings.DeviceSettingsRepository
import ch.qscqlmpa.wingtracker.sync.JoinLogbook
import ch.qscqlmpa.wingtracker.sync.LogbookSync
import ch.qscqlmpa.wingtracker.sync.SyncException
import ch.qscqlmpa.wingtracker.sync.SyncProblem
import ch.qscqlmpa.wingtracker.sync.SyncSettings
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
import ch.qscqlmpa.wingtracker.ui.platform.DataLocation
import ch.qscqlmpa.wingtracker.ui.platform.FolderCheck
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
private const val CURRENT = "/home/michael/.local/share/wing-tracker"
private const val CHOSEN = "/home/michael/Flugbuch"

private val SETTINGS =
    SyncSettings(
        repository = "https://codeberg.org/michael/logbook.git",
        username = "michael",
        authorName = "Michael Heiniger",
        authorEmail = "michael@example.org",
    )

@OptIn(ExperimentalCoroutinesApi::class)
class JoinPresenterTest {
    private val sync = mockk<LogbookSync>(relaxUnitFun = true)
    private val deviceSettings =
        mockk<DeviceSettingsRepository>(relaxUnitFun = true).also {
            coEvery { it.load() } returns DeviceSettings(interfaceScalePercent = 125)
        }
    private val credentials = mockk<CredentialStore>(relaxUnitFun = true)
    private val location =
        mockk<DataLocation>(relaxUnitFun = true).also {
            every { it.path } returns CURRENT
            coEvery { it.chooseFolder(any()) } returns CHOSEN
            coEvery { it.inspect(CHOSEN) } returns FolderCheck.Empty
        }
    private var flushed = 0

    private fun TestScope.offered() =
        JoinPresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            JoinLogbook(sync, deviceSettings, AppClock()),
            location,
            SyncSecret(credentials),
            "Choose an empty folder",
            beforeJoin = { flushed++ },
        ).also { it.offer(SETTINGS, TOKEN) }

    @Nested
    inner class ChooseFolder {
        @Test
        fun `copies the logbook into the chosen folder, keeps the token and switches to it on restart`() =
            runTest {
                // given
                val presenter = offered()

                // when
                presenter.chooseFolder()

                // then
                coVerify(exactly = 1) { sync.join(SETTINGS.remote(TOKEN), SETTINGS.author, CHOSEN) }
                coVerify(exactly = 1) { credentials.write("sync-token", TOKEN) }
                coVerify(exactly = 1) { location.change(CHOSEN, moveData = false) }
                assertThat(presenter.step).isEqualTo(JoinStep.Joined(CHOSEN))
                assertThat(flushed).isEqualTo(1)
            }

        @Test
        fun `refuses a folder that already holds files`() =
            runTest {
                // given
                coEvery { location.inspect(CHOSEN) } returns FolderCheck.HasData
                val presenter = offered()

                // when
                presenter.chooseFolder()

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.RestoreFolderNotEmpty, CHOSEN))
                assertThat(presenter.step).isEqualTo(JoinStep.Offered(SETTINGS, TOKEN))
                coVerify(exactly = 0) { sync.join(any(), any(), any()) }
            }

        @Test
        fun `goes back to the offer and changes nothing when the copy fails`() =
            runTest {
                // given
                coEvery { sync.join(any(), any(), any()) } throws SyncException(SyncProblem.NoConnection)
                val presenter = offered()

                // when
                presenter.chooseFolder()

                // then
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.NoConnection)
                assertThat(presenter.step).isEqualTo(JoinStep.Offered(SETTINGS, TOKEN))
                coVerify(exactly = 0) { location.change(any(), any()) }
                coVerify(exactly = 0) { credentials.write(any(), any()) }
            }

        @Test
        fun `does nothing when the folder dialog is cancelled`() =
            runTest {
                // given
                coEvery { location.chooseFolder(any()) } returns null
                val presenter = offered()

                // when
                presenter.chooseFolder()

                // then
                assertThat(presenter.step).isEqualTo(JoinStep.Offered(SETTINGS, TOKEN))
                assertThat(presenter.failure).isNull()
            }
    }

    @Nested
    inner class Cancel {
        @Test
        fun `stops a copy still running and closes the offer`() =
            runTest {
                // given
                coEvery { sync.join(any(), any(), any()) } coAnswers { awaitCancellation() }
                val presenter = offered()
                presenter.chooseFolder()

                // when
                presenter.cancel()

                // then
                assertThat(presenter.step).isNull()
                coVerify(exactly = 0) { location.change(any(), any()) }
            }
    }
}
