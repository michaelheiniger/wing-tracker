package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.ui.platform.FolderCheck
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DesktopDataLocationTest {
    @TempDir
    lateinit var root: Path

    private val current get() = root.resolve("share/wing-tracker")
    private val pointer get() = root.resolve("share/wing-tracker.location")

    private fun location(): DesktopDataLocation {
        current.createDirectories()
        current.resolve("flights").createDirectories()
        current.resolve("flights/f-1.json").writeText("""{"id":"f-1"}""")
        current.resolve("settings.json").writeText("{}")
        return DesktopDataLocation(current, current, pointer)
    }

    @Nested
    inner class Inspecting {
        @Test
        fun `recognises the folder in use`() =
            runTest {
                assertThat(location().inspect(current.toString())).isEqualTo(FolderCheck.Same)
            }

        @Test
        fun `treats a missing or empty folder as free to receive the data`() =
            runTest {
                // given
                val location = location()
                root.resolve("empty").createDirectories()

                // then
                assertThat(location.inspect(root.resolve("empty").toString())).isEqualTo(FolderCheck.Empty)
                assertThat(location.inspect(root.resolve("new/place").toString())).isEqualTo(FolderCheck.Empty)
            }

        @Test
        fun `treats a folder with files as holding data`() =
            runTest {
                // given
                val location = location()
                root
                    .resolve("backup")
                    .createDirectories()
                    .resolve("settings.json")
                    .writeText("{}")

                // then
                assertThat(location.inspect(root.resolve("backup").toString())).isEqualTo(FolderCheck.HasData)
            }

        @Test
        fun `refuses a folder inside the current one and one that contains it`() =
            runTest {
                // given
                val location = location()

                // then
                assertThat(location.inspect(current.resolve("sub").toString())).isEqualTo(FolderCheck.Overlaps)
                assertThat(location.inspect(root.toString())).isEqualTo(FolderCheck.Overlaps)
            }

        @Test
        fun `refuses a file as the data folder`() =
            runTest {
                // given
                val location = location()
                val file = root.resolve("a-file").also { it.writeText("x") }

                // then
                assertThat(location.inspect(file.toString())).isEqualTo(FolderCheck.Unusable)
            }
    }

    @Nested
    inner class Changing {
        @Test
        fun `moves the data and remembers the new folder`() =
            runTest {
                // given
                val location = location()
                val target = root.resolve("elsewhere")

                // when
                location.change(target.toString(), moveData = true)

                // then
                assertThat(target.resolve("flights/f-1.json").readText()).isEqualTo("""{"id":"f-1"}""")
                assertThat(target.resolve("settings.json").exists()).isTrue()
                assertThat(current.resolve("flights/f-1.json").exists()).isFalse()
                assertThat(pointer.readText()).isEqualTo(target.toString())
            }

        @Test
        fun `switches without touching the old data when not moving`() =
            runTest {
                // given
                val location = location()
                val backup = root.resolve("backup").createDirectories()
                backup.resolve("settings.json").writeText("{}")

                // when
                location.change(backup.toString(), moveData = false)

                // then
                assertThat(current.resolve("flights/f-1.json").exists()).isTrue()
                assertThat(pointer.readText()).isEqualTo(backup.toString())
            }

        @Test
        fun `refuses to move into a folder that already holds data`() =
            runTest {
                // given
                val location = location()
                val backup = root.resolve("backup").createDirectories()
                backup.resolve("settings.json").writeText("{}")

                // when / then
                assertThatThrownBy {
                    kotlinx.coroutines.runBlocking {
                        location.change(
                            backup.toString(),
                            moveData = true,
                        )
                    }
                }.isInstanceOf(IllegalStateException::class.java)
                assertThat(pointer.exists()).isFalse()
                assertThat(current.resolve("flights/f-1.json").exists()).isTrue()
            }

        @Test
        fun `forgets the choice when going back to the default folder`() =
            runTest {
                // given
                val default = root.resolve("share/wing-tracker")
                val elsewhere = root.resolve("elsewhere").createDirectories()
                elsewhere.resolve("settings.json").writeText("{}")
                val location = DesktopDataLocation(elsewhere, default, pointer)
                pointer.parent.createDirectories()
                pointer.writeText(elsewhere.toString())

                // when
                location.change(default.toString(), moveData = false)

                // then
                assertThat(pointer.exists()).isFalse()
            }
    }

    @Nested
    inner class ResolvingTheFolderInUse {
        private fun configured(env: Map<String, String> = mapOf("XDG_DATA_HOME" to root.resolve("share").toString())) =
            DataDirectory.configured(false, "Linux", env, root.toString())

        @Test
        fun `uses the platform default when nothing was chosen`() {
            assertThat(configured()).isEqualTo(root.resolve("share/wing-tracker"))
        }

        @Test
        fun `uses the chosen folder when it exists`() {
            // given
            val chosen = root.resolve("elsewhere").createDirectories()
            pointer.parent.createDirectories()
            pointer.writeText("$chosen\n")

            // then
            assertThat(configured()).isEqualTo(chosen)
        }

        @Test
        fun `falls back to the default when the chosen folder is gone`() {
            // given
            pointer.parent.createDirectories()
            pointer.writeText(root.resolve("unplugged-drive").toString())

            // then
            assertThat(configured()).isEqualTo(root.resolve("share/wing-tracker"))
        }

        @Test
        fun `keeps development runs apart from the real logbook`() {
            assertThat(DataDirectory.pointerFile(true, "Linux", emptyMap(), "/home/m").fileName.toString())
                .isEqualTo("wing-tracker-dev.location")
        }
    }
}
