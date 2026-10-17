package ch.qscqlmpa.wingtracker.updates

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val DOWNLOADS = "https://github.com/michaelheiniger/wing-tracker/releases/download/v0.2.0"
private const val DEB_NAME = "wing-tracker_0.2.0_amd64.deb"
private const val DEB_CHECKSUM = "73cb3858a687a8494ca3323053016282f3dad39d42cf62ca4e79dda2aac7d9ac"
private const val OTHER_CHECKSUM = "0f343b0931126a20f133d67c2b018a3b2a1e9d2e4b0e6c7d5f7e8a9b0c1d2e3f"
private const val CACHED = "/home/michael/.cache/wing-tracker/updates/$DEB_NAME"

private val deb = ReleaseAsset(DEB_NAME, "$DOWNLOADS/$DEB_NAME")
private val sums = ReleaseAsset("SHA256SUMS", "$DOWNLOADS/SHA256SUMS")

private fun anUpdate(assets: List<ReleaseAsset> = listOf(deb, sums)) =
    AvailableUpdate(
        releases =
            listOf(
                Release(
                    version = AppVersion(0, 2, 0),
                    notes = "### Added\n- Install updates from the app.",
                    pageUrl = "https://github.com/michaelheiniger/wing-tracker/releases/tag/v0.2.0",
                    assets = assets,
                ),
            ),
        installer = deb,
        skipped = false,
    )

class DownloadUpdateTest {
    private val downloads = mockk<ReleaseDownloads>(relaxUnitFun = true)
    private val progress = mutableListOf<Pair<Long, Long?>>()

    private suspend fun download(update: AvailableUpdate = anUpdate()) =
        DownloadUpdate(downloads)(update) { received, total -> progress += received to total }

    @Nested
    inner class Invoke {
        @Test
        fun `keeps an installer whose checksum is the published one`() =
            runTest {
                // given
                coEvery { downloads.readText(sums) } returns "$DEB_CHECKSUM  $DEB_NAME\n"
                coEvery { downloads.download(deb, any()) } answers {
                    secondArg<(Long, Long?) -> Unit>()(1_024L, 2_048L)
                    DownloadedFile(CACHED, DEB_CHECKSUM)
                }

                // when
                val result = download()

                // then
                assertThat(result).isEqualTo(DownloadResult.Verified(CACHED))
                assertThat(progress).containsExactly(1_024L to 2_048L)
            }

        @Test
        fun `discards an installer whose checksum differs`() =
            runTest {
                // given
                coEvery { downloads.readText(sums) } returns "$DEB_CHECKSUM  $DEB_NAME\n"
                val file = DownloadedFile(CACHED, OTHER_CHECKSUM)
                coEvery { downloads.download(deb, any()) } returns file

                // when
                val result = download()

                // then
                assertThat(result).isEqualTo(DownloadResult.Failed(DownloadProblem.ChecksumMismatch, DEB_NAME))
                coVerify(exactly = 1) { downloads.discard(file) }
            }

        @Test
        fun `refuses a release that publishes no checksums, without downloading`() =
            runTest {
                // when
                val result = download(anUpdate(assets = listOf(deb)))

                // then
                assertThat(result).isEqualTo(DownloadResult.Failed(DownloadProblem.NoChecksum, null))
                coVerify(exactly = 0) { downloads.download(any(), any()) }
            }

        @Test
        fun `refuses an installer the checksum file does not list, without downloading`() =
            runTest {
                // given
                coEvery { downloads.readText(sums) } returns "$DEB_CHECKSUM  wing-tracker_0.1.0_amd64.deb\n"

                // when
                val result = download()

                // then
                assertThat(result).isEqualTo(DownloadResult.Failed(DownloadProblem.NoChecksum, DEB_NAME))
                coVerify(exactly = 0) { downloads.download(any(), any()) }
            }

        @Test
        fun `reports why a file could not be had`() =
            runTest {
                // given
                coEvery { downloads.readText(sums) } returns "$DEB_CHECKSUM  $DEB_NAME\n"
                coEvery { downloads.download(deb, any()) } throws
                    ReleaseSourceException(ReleaseProblem.Unreachable, "Connection reset")

                // when
                val result = download()

                // then
                assertThat(result).isEqualTo(DownloadResult.Failed(DownloadProblem.Unreachable, "Connection reset"))
            }

        @Test
        fun `reports that no connection could be made`() =
            runTest {
                // given
                coEvery { downloads.readText(sums) } throws ReleaseSourceException(ReleaseProblem.NoConnection)

                // when
                val result = download()

                // then
                assertThat(result).isEqualTo(DownloadResult.Failed(DownloadProblem.NoConnection, "NoConnection"))
                coVerify(exactly = 0) { downloads.download(any(), any()) }
            }
    }
}
