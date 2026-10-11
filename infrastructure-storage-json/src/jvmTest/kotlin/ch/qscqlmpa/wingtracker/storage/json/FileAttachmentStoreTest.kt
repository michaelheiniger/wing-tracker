package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

class FileAttachmentStoreTest {
    @TempDir
    lateinit var dataDirectory: Path

    private val store by lazy { FileAttachmentStore(dataDirectory) }
    private val id = AttachmentId("report-1")
    private val pdf = byteArrayOf(37, 80, 68, 70, 45, 49, 46, 52)

    @Nested
    inner class SaveAndRead {
        @Test
        fun `returns exactly the bytes that were saved`() =
            runTest {
                // given
                store.save(id, pdf)

                // when
                val bytes = store.read(id)

                // then
                assertThat(bytes).isEqualTo(pdf)
            }

        @Test
        fun `replaces the content when saved again under the same id`() =
            runTest {
                // given
                store.save(id, pdf)
                store.save(id, byteArrayOf(1, 2, 3))

                // when
                val bytes = store.read(id)

                // then
                assertThat(bytes).isEqualTo(byteArrayOf(1, 2, 3))
            }

        @Test
        fun `leaves no temporary file behind`() =
            runTest {
                // given / when
                store.save(id, pdf)

                // then
                val names = dataDirectory.resolve("attachments").listDirectoryEntries().map { it.fileName.toString() }
                assertThat(names).containsExactly("report-1")
            }

        @Test
        fun `fails to read an attachment that does not exist`() =
            runTest {
                // given / when
                val failure = runCatching { store.read(AttachmentId("missing")) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(IOException::class.java)
            }
    }

    @Nested
    inner class Delete {
        @Test
        fun `removes the file`() =
            runTest {
                // given
                store.save(id, pdf)

                // when
                store.delete(id)

                // then
                assertThat(runCatching { store.read(id) }.isFailure).isTrue()
            }

        @Test
        fun `does nothing when the file is already gone`() =
            runTest {
                // given / when
                val failure = runCatching { store.delete(AttachmentId("missing")) }

                // then
                assertThat(failure.isSuccess).isTrue()
            }
    }
}
