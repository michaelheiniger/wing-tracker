package ch.qscqlmpa.wingtracker.ui.updates

import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.updates.AppVersion
import ch.qscqlmpa.wingtracker.updates.AvailableUpdate
import ch.qscqlmpa.wingtracker.updates.CheckForUpdate
import ch.qscqlmpa.wingtracker.updates.CheckTrigger
import ch.qscqlmpa.wingtracker.updates.DownloadUpdate
import ch.qscqlmpa.wingtracker.updates.GetUpdatePreferences
import ch.qscqlmpa.wingtracker.updates.Release
import ch.qscqlmpa.wingtracker.updates.ReleaseAsset
import ch.qscqlmpa.wingtracker.updates.ReleaseProblem
import ch.qscqlmpa.wingtracker.updates.SaveCheckAtStartup
import ch.qscqlmpa.wingtracker.updates.SkipVersion
import ch.qscqlmpa.wingtracker.updates.UpdateCheckResult
import ch.qscqlmpa.wingtracker.updates.UpdatePreferences
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
import java.io.IOException

private const val RELEASES = "https://github.com/michaelheiniger/wing-tracker/releases"

private fun anUpdate(skipped: Boolean = false) =
    AvailableUpdate(
        releases =
            listOf(
                Release(
                    version = AppVersion(0, 1, 0),
                    notes = "### Added\n- Wing Tracker tells you when a new version is out.",
                    pageUrl = "$RELEASES/tag/v0.1.0",
                    assets = emptyList(),
                ),
            ),
        installer =
            ReleaseAsset("wing-tracker_0.1.0_amd64.deb", "$RELEASES/download/v0.1.0/wing-tracker_0.1.0_amd64.deb"),
        skipped = skipped,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatePresenterTest {
    private val checkForUpdate = mockk<CheckForUpdate>()
    private val getPreferences = mockk<GetUpdatePreferences>()
    private val saveCheckAtStartup = mockk<SaveCheckAtStartup>(relaxUnitFun = true)
    private val skipVersion = mockk<SkipVersion>(relaxUnitFun = true)

    private fun TestScope.presenter() =
        UpdatePresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            UpdateUseCases(checkForUpdate, getPreferences, saveCheckAtStartup, skipVersion, mockk<DownloadUpdate>()),
        )

    private fun givenStartupFinds(result: UpdateCheckResult) {
        coEvery { getPreferences() } returns UpdatePreferences(checkAtStartup = true)
        coEvery { checkForUpdate(CheckTrigger.Startup) } returns result
    }

    @Nested
    inner class Start {
        @Test
        fun `shows the banner and the badge for an update found at startup`() =
            runTest {
                // given
                givenStartupFinds(UpdateCheckResult.Available(anUpdate()))

                // when
                val presenter = presenter().also { it.start() }

                // then
                assertThat(presenter.update).isEqualTo(anUpdate())
                assertThat(presenter.showsBanner).isTrue()
                assertThat(presenter.showsBadge).isTrue()
            }

        @Test
        fun `shows nothing for a skipped update`() =
            runTest {
                // given
                givenStartupFinds(UpdateCheckResult.Available(anUpdate(skipped = true)))

                // when
                val presenter = presenter().also { it.start() }

                // then
                assertThat(presenter.showsBanner).isFalse()
                assertThat(presenter.showsBadge).isFalse()
            }

        @Test
        fun `keeps quiet about a failed check at startup`() =
            runTest {
                // given
                givenStartupFinds(UpdateCheckResult.Failed(ReleaseProblem.Unreachable, "timeout"))

                // when
                val presenter = presenter().also { it.start() }

                // then
                assertThat(presenter.update).isNull()
                assertThat(presenter.failure).isNull()
                assertThat(presenter.manualCheck).isEqualTo(ManualCheck.Idle)
            }

        @Test
        fun `shows the stored startup choice`() =
            runTest {
                // given
                coEvery { getPreferences() } returns UpdatePreferences(checkAtStartup = false)
                coEvery { checkForUpdate(CheckTrigger.Startup) } returns UpdateCheckResult.Disabled

                // when
                val presenter = presenter().also { it.start() }

                // then
                assertThat(presenter.checkAtStartup).isFalse()
            }
    }

    @Nested
    inner class Postpone {
        @Test
        fun `hides the banner for this run and keeps the badge`() =
            runTest {
                // given
                givenStartupFinds(UpdateCheckResult.Available(anUpdate()))
                val presenter = presenter().also { it.start() }

                // when
                presenter.postpone()

                // then
                assertThat(presenter.showsBanner).isFalse()
                assertThat(presenter.showsBadge).isTrue()
                coVerify(exactly = 0) { skipVersion(any()) }
            }
    }

    @Nested
    inner class Skip {
        @Test
        fun `remembers the version and hides the banner and the badge`() =
            runTest {
                // given
                givenStartupFinds(UpdateCheckResult.Available(anUpdate()))
                val presenter = presenter().also { it.start() }

                // when
                presenter.skip()

                // then
                coVerify(exactly = 1) { skipVersion(AppVersion(0, 1, 0)) }
                assertThat(presenter.showsBanner).isFalse()
                assertThat(presenter.showsBadge).isFalse()
            }

        @Test
        fun `shows why the choice could not be saved and keeps announcing the update`() =
            runTest {
                // given
                givenStartupFinds(UpdateCheckResult.Available(anUpdate()))
                coEvery { skipVersion(any()) } throws IOException("disk full")
                val presenter = presenter().also { it.start() }

                // when
                presenter.skip()

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.SaveSettings, "disk full"))
                assertThat(presenter.showsBadge).isTrue()
            }
    }

    @Nested
    inner class CheckNow {
        @Test
        fun `says the installation is up to date`() =
            runTest {
                // given
                coEvery { checkForUpdate(CheckTrigger.Manual) } returns UpdateCheckResult.UpToDate
                val presenter = presenter()

                // when
                presenter.checkNow()

                // then
                assertThat(presenter.manualCheck).isEqualTo(ManualCheck.UpToDate)
                assertThat(presenter.update).isNull()
            }

        @Test
        fun `brings back an update the pilot skipped or postponed`() =
            runTest {
                // given
                givenStartupFinds(UpdateCheckResult.Available(anUpdate()))
                coEvery { checkForUpdate(CheckTrigger.Manual) } returns
                    UpdateCheckResult.Available(anUpdate(skipped = true))
                val presenter = presenter().also { it.start() }
                presenter.postpone()

                // when
                presenter.checkNow()

                // then
                assertThat(presenter.manualCheck).isEqualTo(ManualCheck.Found)
                assertThat(presenter.showsBanner).isTrue()
                assertThat(presenter.showsBadge).isTrue()
            }

        @Test
        fun `shows why the releases could not be read`() =
            runTest {
                // given
                coEvery { checkForUpdate(CheckTrigger.Manual) } returns
                    UpdateCheckResult.Failed(ReleaseProblem.Refused, "GitHub answered 403")
                val presenter = presenter()

                // when
                presenter.checkNow()

                // then
                assertThat(presenter.manualCheck).isEqualTo(
                    ManualCheck.Failed(Failure(FailureKind.UpdateRefused, "GitHub answered 403")),
                )
            }

        @Test
        fun `says so when no connection could be made`() =
            runTest {
                // given
                coEvery { checkForUpdate(CheckTrigger.Manual) } returns
                    UpdateCheckResult.Failed(ReleaseProblem.NoConnection, null)
                val presenter = presenter()

                // when
                presenter.checkNow()

                // then
                assertThat(presenter.manualCheck).isEqualTo(ManualCheck.Failed(Failure(FailureKind.NoConnection, null)))
            }

        @Test
        fun `stops checking when the pilot cancels`() =
            runTest {
                // given
                coEvery { checkForUpdate(CheckTrigger.Manual) } coAnswers { awaitCancellation() }
                val presenter = presenter().also { it.checkNow() }

                // when
                presenter.cancelCheck()

                // then
                assertThat(presenter.manualCheck).isEqualTo(ManualCheck.Idle)
                assertThat(presenter.failure).isNull()
            }

        @Test
        fun `says a development build is never updated`() =
            runTest {
                // given
                coEvery { checkForUpdate(CheckTrigger.Manual) } returns UpdateCheckResult.NotApplicable
                val presenter = presenter()

                // when
                presenter.checkNow()

                // then
                assertThat(presenter.manualCheck).isEqualTo(ManualCheck.NotApplicable)
            }
    }

    @Nested
    inner class ChangeCheckAtStartup {
        @Test
        fun `saves and shows the new choice`() =
            runTest {
                // given
                val presenter = presenter()

                // when
                presenter.changeCheckAtStartup(false)

                // then
                coVerify(exactly = 1) { saveCheckAtStartup(false) }
                assertThat(presenter.checkAtStartup).isFalse()
            }
    }

    @Nested
    inner class WhatsNew {
        @Test
        fun `opens only once an update is known`() =
            runTest {
                // given
                val presenter = presenter()

                // when
                presenter.openWhatsNew()

                // then
                assertThat(presenter.showsWhatsNew).isFalse()
            }

        @Test
        fun `opens and closes for a known update`() =
            runTest {
                // given
                givenStartupFinds(UpdateCheckResult.Available(anUpdate()))
                val presenter = presenter().also { it.start() }

                // when
                presenter.openWhatsNew()
                val opened = presenter.showsWhatsNew
                presenter.closeWhatsNew()

                // then
                assertThat(opened).isTrue()
                assertThat(presenter.showsWhatsNew).isFalse()
            }
    }
}
