package ch.qscqlmpa.wingtracker.github

import ch.qscqlmpa.wingtracker.updates.AppVersion
import ch.qscqlmpa.wingtracker.updates.ReleaseAsset
import ch.qscqlmpa.wingtracker.updates.ReleaseProblem
import ch.qscqlmpa.wingtracker.updates.ReleaseSourceException
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

private const val REPOSITORY = "michaelheiniger/wing-tracker"
private const val USER_AGENT = "wing-tracker/0.1.0"

// The release list GitHub answered for this repository, without the accounts that published it.
private val SAMPLE =
    checkNotNull(GitHubReleaseSourceTest::class.java.getResource("/releases-sample.json")).readText()

private fun aRelease(
    tag: String,
    draft: Boolean = false,
    prerelease: Boolean = false,
) = """
    {
      "tag_name": "$tag",
      "html_url": "https://github.com/$REPOSITORY/releases/tag/$tag",
      "body": "### Added\n- Something new.",
      "draft": $draft,
      "prerelease": $prerelease,
      "assets": []
    }
    """

class GitHubReleaseSourceTest {
    private val requested = mutableListOf<Pair<String, String>>()

    private fun source(answer: HttpAnswer) =
        GitHubReleaseSource(REPOSITORY, USER_AGENT) { url, userAgent ->
            requested += url to userAgent
            answer
        }

    @Nested
    inner class Published {
        @Test
        fun `reads every release with its notes, page and installers`() =
            runTest {
                // when
                val releases = source(HttpAnswer(200, SAMPLE)).published()

                // then
                assertThat(releases.map { it.version.toString() }).containsExactly("0.1.0", "0.0.3", "0.0.2", "0.0.1")
                val latest = releases.first()
                assertThat(latest.pageUrl).isEqualTo("https://github.com/$REPOSITORY/releases/tag/v0.1.0")
                assertThat(latest.notes).contains("### Changed", "one \"Import\" menu")
                assertThat(latest.assets).containsExactly(
                    ReleaseAsset(
                        "wing-tracker_0.1.0_amd64.deb",
                        "https://github.com/$REPOSITORY/releases/download/v0.1.0/wing-tracker_0.1.0_amd64.deb",
                    ),
                )
            }

        @Test
        fun `asks the API of the repository, naming the application`() =
            runTest {
                // when
                source(HttpAnswer(200, "[]")).published()

                // then
                assertThat(requested).containsExactly(
                    "https://api.github.com/repos/$REPOSITORY/releases?per_page=100" to USER_AGENT,
                )
            }

        @Test
        fun `leaves out drafts, pre-releases and tags that are no version`() =
            runTest {
                // given
                val body =
                    "[" +
                        listOf(
                            aRelease("v0.2.0", draft = true),
                            aRelease("v0.2.0-beta", prerelease = true),
                            aRelease("nightly"),
                            aRelease("v0.1.1"),
                        ).joinToString(",") + "]"

                // when
                val releases = source(HttpAnswer(200, body)).published()

                // then
                assertThat(releases.map { it.version }).containsExactly(AppVersion(0, 1, 1))
            }

        @Test
        fun `reads a release without notes as one with empty notes`() =
            runTest {
                // given
                val body =
                    """[{"tag_name": "v0.1.1", "html_url": "https://github.com/$REPOSITORY/releases/tag/v0.1.1",""" +
                        """ "body": null, "assets": []}]"""

                // when
                val releases = source(HttpAnswer(200, body)).published()

                // then
                assertThat(releases.single().notes).isEmpty()
            }

        @ParameterizedTest
        @CsvSource("403, Refused", "429, Refused", "500, Unreachable", "503, Unreachable", "404, Unexpected")
        fun `reports what GitHub's refusal means`(
            status: Int,
            problem: ReleaseProblem,
        ) = runTest {
            // when / then
            assertThatThrownBy { kotlinx.coroutines.runBlocking { source(HttpAnswer(status, "{}")).published() } }
                .isInstanceOfSatisfying(ReleaseSourceException::class.java) {
                    assertThat(it.problem).isEqualTo(problem)
                }
        }

        @Test
        fun `reports an answer it cannot read as unexpected`() =
            runTest {
                // when / then
                assertThatThrownBy {
                    kotlinx.coroutines.runBlocking { source(HttpAnswer(200, "<html>Sign in</html>")).published() }
                }.isInstanceOfSatisfying(ReleaseSourceException::class.java) {
                    assertThat(it.problem).isEqualTo(ReleaseProblem.Unexpected)
                }
            }
    }
}
