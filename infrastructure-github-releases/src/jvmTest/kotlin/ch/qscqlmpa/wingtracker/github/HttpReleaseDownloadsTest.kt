package ch.qscqlmpa.wingtracker.github

import ch.qscqlmpa.wingtracker.updates.DownloadedFile
import ch.qscqlmpa.wingtracker.updates.ReleaseAsset
import ch.qscqlmpa.wingtracker.updates.ReleaseProblem
import ch.qscqlmpa.wingtracker.updates.ReleaseSourceException
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

private const val USER_AGENT = "wing-tracker/0.1.0"
private const val DOWNLOADS = "https://github.com/michaelheiniger/wing-tracker/releases/download/v0.2.0"
private const val DEB_NAME = "wing-tracker_0.2.0_amd64.deb"

// "!Debian package" checked with `printf '!Debian package' | sha256sum`.
private val DEB_BYTES = "!Debian package".toByteArray()
private const val DEB_CHECKSUM = "64551f3eef57930a6ea94d9f1a1b9002aebb1f21b20ec576c21b5a226e6ab8d3"

private val deb = ReleaseAsset(DEB_NAME, "$DOWNLOADS/$DEB_NAME")

class HttpReleaseDownloadsTest {
    @TempDir
    lateinit var directory: Path

    private val opened = mutableListOf<String>()

    private fun downloads(
        status: Int = 200,
        body: InputStream = ByteArrayInputStream(DEB_BYTES),
        length: Long? = DEB_BYTES.size.toLong(),
        text: HttpAnswer = HttpAnswer(200, ""),
    ) = HttpReleaseDownloads(
        directory,
        USER_AGENT,
        get = { _, _ -> text },
        open = { url, _ ->
            opened += url
            HttpStream(status, length, body)
        },
    )

    @Nested
    inner class Download {
        @Test
        fun `saves the file and reports what arrived and its checksum`() =
            runTest {
                // given
                val progress = mutableListOf<Pair<Long, Long?>>()

                // when
                val file = downloads().download(deb) { received, total -> progress += received to total }

                // then
                assertThat(opened).containsExactly("$DOWNLOADS/$DEB_NAME")
                assertThat(Path.of(file.path)).isEqualTo(directory.resolve(DEB_NAME))
                assertThat(Path.of(file.path).readBytes()).isEqualTo(DEB_BYTES)
                assertThat(file.sha256).isEqualTo(DEB_CHECKSUM)
                assertThat(progress.last()).isEqualTo(DEB_BYTES.size.toLong() to DEB_BYTES.size.toLong())
            }

        @Test
        fun `replaces an earlier download, which is of no use any more`() =
            runTest {
                // given
                directory.resolve("wing-tracker_0.1.1_amd64.deb").writeText("older")

                // when
                downloads().download(deb) { _, _ -> }

                // then
                assertThat(directory.listDirectoryEntries().map { it.fileName.toString() }).containsExactly(DEB_NAME)
            }

        @Test
        fun `keeps nothing of a download that broke off`() =
            runTest {
                // given
                val breaking =
                    object : InputStream() {
                        private var sent = false

                        override fun read(): Int = throw IOException("Connection reset")

                        override fun read(
                            b: ByteArray,
                            off: Int,
                            len: Int,
                        ): Int {
                            if (sent) throw IOException("Connection reset")
                            sent = true
                            b[off] = 1
                            return 1
                        }
                    }

                // when / then
                assertThatThrownBy {
                    kotlinx.coroutines.runBlocking { downloads(body = breaking).download(deb) { _, _ -> } }
                }.isInstanceOfSatisfying(ReleaseSourceException::class.java) {
                    assertThat(it.problem).isEqualTo(ReleaseProblem.Unreachable)
                }
                assertThat(directory.listDirectoryEntries()).isEmpty()
            }

        @Test
        fun `reports a refusal and downloads nothing`() =
            runTest {
                // when / then
                assertThatThrownBy {
                    kotlinx.coroutines.runBlocking { downloads(status = 403).download(deb) { _, _ -> } }
                }.isInstanceOfSatisfying(ReleaseSourceException::class.java) {
                    assertThat(it.problem).isEqualTo(ReleaseProblem.Refused)
                }
                assertThat(directory.resolve(DEB_NAME).exists()).isFalse()
            }

        @Test
        fun `never writes outside its folder, whatever the file is called`() =
            runTest {
                // when
                val file = downloads().download(ReleaseAsset("../../.bashrc", "$DOWNLOADS/x")) { _, _ -> }

                // then
                assertThat(Path.of(file.path)).isEqualTo(directory.resolve(".bashrc"))
            }
    }

    @Nested
    inner class ReadText {
        @Test
        fun `reads a small file such as the checksums`() =
            runTest {
                // given
                val text = "$DEB_CHECKSUM  $DEB_NAME\n"

                // when
                val read = downloads(text = HttpAnswer(200, text)).readText(ReleaseAsset("SHA256SUMS", "$DOWNLOADS/x"))

                // then
                assertThat(read).isEqualTo(text)
            }

        @Test
        fun `reports a missing file as unexpected`() =
            runTest {
                assertThatThrownBy {
                    kotlinx.coroutines.runBlocking {
                        downloads(text = HttpAnswer(404, "Not Found")).readText(ReleaseAsset("SHA256SUMS", "x"))
                    }
                }.isInstanceOfSatisfying(ReleaseSourceException::class.java) {
                    assertThat(it.problem).isEqualTo(ReleaseProblem.Unexpected)
                }
            }
    }

    @Nested
    inner class Discard {
        @Test
        fun `removes the file`() =
            runTest {
                // given
                val path = directory.resolve(DEB_NAME).also { it.writeText("tampered") }

                // when
                downloads().discard(DownloadedFile(path.toString(), DEB_CHECKSUM))

                // then
                assertThat(path.exists()).isFalse()
            }
    }
}
