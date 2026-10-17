package ch.qscqlmpa.wingtracker.updates

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val RELEASES = "https://github.com/michaelheiniger/wing-tracker/releases"

private fun aPublishedRelease(version: String) =
    Release(
        version = checkNotNull(AppVersion.parseOrNull(version)),
        notes = "### Fixed\n- The Syride import works again in the installed app.",
        pageUrl = "$RELEASES/tag/v$version",
        assets =
            listOf(
                ReleaseAsset(
                    "wing-tracker_${version}_amd64.deb",
                    "$RELEASES/download/v$version/wing-tracker_${version}_amd64.deb",
                ),
            ),
    )

class UpdateUseCasesTest {
    private val installed = AppVersion(0, 0, 3)
    private val source = mockk<ReleaseSource>()
    private val preferences = mockk<UpdatePreferencesRepository>(relaxUnitFun = true)
    private val stored = UpdatePreferences(checkAtStartup = true, skippedVersion = null)

    private fun checkForUpdate(version: AppVersion? = installed) =
        CheckForUpdate(source, preferences, version, InstallerKind.Deb)

    @Nested
    inner class CheckingForAnUpdate {
        @Test
        fun `offers the newer release`() =
            runTest {
                // given
                coEvery { preferences.load() } returns stored
                coEvery { source.published() } returns listOf(aPublishedRelease("0.0.3"), aPublishedRelease("0.1.0"))

                // when
                val result = checkForUpdate()(CheckTrigger.Startup)

                // then
                assertThat(result).isInstanceOf(UpdateCheckResult.Available::class.java)
                val update = (result as UpdateCheckResult.Available).update
                assertThat(update.latest).isEqualTo(aPublishedRelease("0.1.0"))
                assertThat(update.skipped).isFalse()
            }

        @Test
        fun `says the installation is up to date when nothing newer is published`() =
            runTest {
                // given
                coEvery { preferences.load() } returns stored
                coEvery { source.published() } returns listOf(aPublishedRelease("0.0.3"))

                // when
                val result = checkForUpdate()(CheckTrigger.Manual)

                // then
                assertThat(result).isEqualTo(UpdateCheckResult.UpToDate)
            }

        @Test
        fun `never asks for releases in a development build`() =
            runTest {
                // when
                val result = checkForUpdate(version = null)(CheckTrigger.Manual)

                // then
                assertThat(result).isEqualTo(UpdateCheckResult.NotApplicable)
                coVerify(exactly = 0) { source.published() }
            }

        @Test
        fun `does not check at startup when the pilot switched it off`() =
            runTest {
                // given
                coEvery { preferences.load() } returns stored.copy(checkAtStartup = false)

                // when
                val result = checkForUpdate()(CheckTrigger.Startup)

                // then
                assertThat(result).isEqualTo(UpdateCheckResult.Disabled)
                coVerify(exactly = 0) { source.published() }
            }

        @Test
        fun `still checks when asked to, even with the startup check switched off`() =
            runTest {
                // given
                coEvery { preferences.load() } returns stored.copy(checkAtStartup = false)
                coEvery { source.published() } returns listOf(aPublishedRelease("0.1.0"))

                // when
                val result = checkForUpdate()(CheckTrigger.Manual)

                // then
                assertThat(result).isInstanceOf(UpdateCheckResult.Available::class.java)
            }

        @Test
        fun `marks the update skipped when the pilot skipped that version`() =
            runTest {
                // given
                coEvery { preferences.load() } returns stored.copy(skippedVersion = AppVersion(0, 1, 0))
                coEvery { source.published() } returns listOf(aPublishedRelease("0.1.0"))

                // when
                val result = checkForUpdate()(CheckTrigger.Startup)

                // then
                assertThat((result as UpdateCheckResult.Available).update.skipped).isTrue()
            }

        @Test
        fun `reports why the releases could not be read`() =
            runTest {
                // given
                coEvery { preferences.load() } returns stored
                coEvery { source.published() } throws
                    ReleaseSourceException(ReleaseProblem.Refused, "rate limit exceeded")

                // when
                val result = checkForUpdate()(CheckTrigger.Manual)

                // then
                assertThat(result).isEqualTo(UpdateCheckResult.Failed(ReleaseProblem.Refused, "rate limit exceeded"))
            }
    }

    @Nested
    inner class SkippingAVersion {
        @Test
        fun `remembers the version and keeps the startup choice`() =
            runTest {
                // given
                coEvery { preferences.load() } returns stored.copy(checkAtStartup = false)

                // when
                SkipVersion(preferences)(AppVersion(0, 1, 0))

                // then
                coVerify(exactly = 1) {
                    preferences.save(UpdatePreferences(checkAtStartup = false, skippedVersion = AppVersion(0, 1, 0)))
                }
            }
    }

    @Nested
    inner class SavingTheStartupCheck {
        @Test
        fun `changes the choice and keeps the skipped version`() =
            runTest {
                // given
                coEvery { preferences.load() } returns stored.copy(skippedVersion = AppVersion(0, 1, 0))

                // when
                SaveCheckAtStartup(preferences)(false)

                // then
                coVerify(exactly = 1) {
                    preferences.save(UpdatePreferences(checkAtStartup = false, skippedVersion = AppVersion(0, 1, 0)))
                }
            }
    }
}
