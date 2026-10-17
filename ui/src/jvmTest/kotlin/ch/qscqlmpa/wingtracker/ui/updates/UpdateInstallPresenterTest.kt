package ch.qscqlmpa.wingtracker.ui.updates

import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.InstallOutcome
import ch.qscqlmpa.wingtracker.ui.platform.UpdateInstaller
import ch.qscqlmpa.wingtracker.updates.AppVersion
import ch.qscqlmpa.wingtracker.updates.AvailableUpdate
import ch.qscqlmpa.wingtracker.updates.DownloadProblem
import ch.qscqlmpa.wingtracker.updates.DownloadResult
import ch.qscqlmpa.wingtracker.updates.DownloadUpdate
import ch.qscqlmpa.wingtracker.updates.Release
import ch.qscqlmpa.wingtracker.updates.ReleaseAsset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException

private const val DOWNLOADS = "https://github.com/michaelheiniger/wing-tracker/releases/download/v0.2.0"
private const val CACHED = "/home/michael/.cache/wing-tracker/updates/wing-tracker_0.2.0_amd64.deb"

private val anUpdate =
    AvailableUpdate(
        releases =
            listOf(
                Release(
                    version = AppVersion(0, 2, 0),
                    notes = "### Added\n- Install updates from the app.",
                    pageUrl = "https://github.com/michaelheiniger/wing-tracker/releases/tag/v0.2.0",
                    assets =
                        listOf(
                            ReleaseAsset("wing-tracker_0.2.0_amd64.deb", "$DOWNLOADS/wing-tracker_0.2.0_amd64.deb"),
                            ReleaseAsset("SHA256SUMS", "$DOWNLOADS/SHA256SUMS"),
                        ),
                ),
            ),
        installer = ReleaseAsset("wing-tracker_0.2.0_amd64.deb", "$DOWNLOADS/wing-tracker_0.2.0_amd64.deb"),
        skipped = false,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateInstallPresenterTest {
    private val downloadUpdate = mockk<DownloadUpdate>()
    private val installer = mockk<UpdateInstaller>(relaxUnitFun = true)
    private var flushed = false

    private fun TestScope.presenter() =
        UpdateInstallPresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            downloadUpdate,
            installer,
            beforeRestart = { flushed = true },
        )

    private fun givenDownloaded(result: DownloadResult = DownloadResult.Verified(CACHED)) {
        coEvery { downloadUpdate(anUpdate, any()) } returns result
    }

    @Nested
    inner class Install {
        @Test
        fun `installs the verified installer and offers to restart`() =
            runTest {
                // given
                givenDownloaded()
                coEvery { installer.install(CACHED) } returns InstallOutcome.Installed
                val presenter = presenter()

                // when
                presenter.install(anUpdate)

                // then
                assertThat(presenter.state).isEqualTo(InstallState.Installed)
            }

        @Test
        fun `shows the download's progress`() =
            runTest {
                // given
                val finish = CompletableDeferred<DownloadResult>()
                coEvery { downloadUpdate(anUpdate, any()) } coAnswers {
                    secondArg<(Long, Long?) -> Unit>()(41_943_040L, 83_886_080L)
                    finish.await()
                }
                val presenter = presenter()

                // when
                presenter.install(anUpdate)

                // then
                assertThat(presenter.state).isEqualTo(InstallState.Downloading(41_943_040L, 83_886_080L))
                assertThat((presenter.state as InstallState.Downloading).fraction).isEqualTo(0.5f)
                presenter.cancelDownload()
            }

        @Test
        fun `never hands over an installer whose checksum differs`() =
            runTest {
                // given
                givenDownloaded(DownloadResult.Failed(DownloadProblem.ChecksumMismatch, "wing-tracker_0.2.0_amd64.deb"))
                val presenter = presenter()

                // when
                presenter.install(anUpdate)

                // then
                assertThat(presenter.state).isEqualTo(
                    InstallState.Failed(Failure(FailureKind.UpdateChecksumMismatch, "wing-tracker_0.2.0_amd64.deb")),
                )
                coVerify(exactly = 0) { installer.install(any()) }
            }

        @Test
        fun `keeps the file when the pilot closes the password prompt, and tries again without downloading`() =
            runTest {
                // given
                givenDownloaded()
                coEvery { installer.install(CACHED) } returns InstallOutcome.Cancelled andThen InstallOutcome.Installed
                val presenter = presenter()
                presenter.install(anUpdate)
                val afterCancel = presenter.state

                // when
                presenter.retry()

                // then
                assertThat(afterCancel).isEqualTo(InstallState.Cancelled(CACHED))
                assertThat(presenter.state).isEqualTo(InstallState.Installed)
                coVerify(exactly = 1) { downloadUpdate(any(), any()) }
            }

        @Test
        fun `tells the pilot how to install by hand when the system cannot from the app`() =
            runTest {
                // given
                givenDownloaded()
                coEvery { installer.install(CACHED) } returns InstallOutcome.Manual("sudo apt install '$CACHED'")
                val presenter = presenter()

                // when
                presenter.install(anUpdate)

                // then
                assertThat(presenter.state).isEqualTo(InstallState.Manual(CACHED, "sudo apt install '$CACHED'"))
            }

        @Test
        fun `offers to install while restarting where the system cannot replace a running application`() =
            runTest {
                // given
                givenDownloaded()
                coEvery { installer.install(CACHED) } returns InstallOutcome.InstallsOnRestart
                val presenter = presenter()

                // when
                presenter.install(anUpdate)

                // then
                assertThat(presenter.state).isEqualTo(InstallState.InstallsOnRestart)
            }

        @Test
        fun `shows why installing failed`() =
            runTest {
                // given
                givenDownloaded()
                coEvery { installer.install(CACHED) } returns InstallOutcome.Failed("dpkg: error processing archive")
                val presenter = presenter()

                // when
                presenter.install(anUpdate)

                // then
                assertThat(presenter.state).isEqualTo(
                    InstallState.Failed(Failure(FailureKind.InstallUpdate, "dpkg: error processing archive")),
                )
            }

        @Test
        fun `ends in a message, not stuck installing, when the installer throws`() =
            runTest {
                // given
                givenDownloaded()
                coEvery { installer.install(CACHED) } throws IOException("Cannot run program \"pkexec\"")
                val presenter = presenter()

                // when
                presenter.install(anUpdate)

                // then
                assertThat(presenter.state).isEqualTo(
                    InstallState.Failed(Failure(FailureKind.InstallUpdate, "Cannot run program \"pkexec\"")),
                )
            }
    }

    @Nested
    inner class CancelDownload {
        @Test
        fun `stops the download and closes the dialog`() =
            runTest {
                // given
                val never = CompletableDeferred<DownloadResult>()
                coEvery { downloadUpdate(anUpdate, any()) } coAnswers { never.await() }
                val presenter = presenter()
                presenter.install(anUpdate)

                // when
                presenter.cancelDownload()
                never.complete(DownloadResult.Verified(CACHED))

                // then
                assertThat(presenter.state).isEqualTo(InstallState.Idle)
                coVerify(exactly = 0) { installer.install(any()) }
            }
    }

    @Nested
    inner class Close {
        @Test
        fun `is refused while the system is installing`() =
            runTest {
                // given
                givenDownloaded()
                val installing = CompletableDeferred<InstallOutcome>()
                coEvery { installer.install(CACHED) } coAnswers { installing.await() }
                val presenter = presenter()
                presenter.install(anUpdate)

                // when
                presenter.close()

                // then
                assertThat(presenter.state).isEqualTo(InstallState.Installing)
                installing.complete(InstallOutcome.Installed)
            }
    }

    @Nested
    inner class Restart {
        @Test
        fun `finishes pending work before restarting`() =
            runTest {
                // given
                givenDownloaded()
                coEvery { installer.install(CACHED) } returns InstallOutcome.Installed
                val presenter = presenter()
                presenter.install(anUpdate)

                // when
                presenter.restart()

                // then
                assertThat(flushed).isTrue()
                verify(exactly = 1) { installer.restart() }
            }
    }

    @Nested
    inner class ShowInFolder {
        @Test
        fun `shows the installer kept for installing by hand`() =
            runTest {
                // given
                givenDownloaded()
                coEvery { installer.install(CACHED) } returns InstallOutcome.Manual(null)
                val presenter = presenter()
                presenter.install(anUpdate)

                // when
                presenter.showInFolder()

                // then
                coVerifyOrder {
                    installer.install(CACHED)
                    installer.showInFolder(CACHED)
                }
            }
    }
}
