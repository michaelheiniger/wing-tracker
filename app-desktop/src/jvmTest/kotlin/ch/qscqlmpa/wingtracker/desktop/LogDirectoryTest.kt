package ch.qscqlmpa.wingtracker.desktop

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path

class LogDirectoryTest {
    @Nested
    inner class Resolve {
        @Test
        fun `uses XDG state home on Linux when set`() {
            // given / when
            val env = mapOf("XDG_STATE_HOME" to "/home/michael/.state")
            val path = LogDirectory.resolve(false, "Linux", env, "/home/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/home/michael/.state/wing-tracker/logs"))
        }

        @Test
        fun `falls back to the local state folder on Linux`() {
            // given / when
            val path = LogDirectory.resolve(false, "Linux", emptyMap(), "/home/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/home/michael/.local/state/wing-tracker/logs"))
        }

        @Test
        fun `uses Library Logs on macOS`() {
            // given / when
            val path = LogDirectory.resolve(false, "Mac OS X", emptyMap(), "/Users/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/Users/michael/Library/Logs/wing-tracker"))
        }

        @Test
        fun `uses LOCALAPPDATA on Windows`() {
            // given / when
            val path =
                LogDirectory.resolve(
                    false,
                    "Windows 11",
                    mapOf("LOCALAPPDATA" to "C:/Users/michael/AppData/Local"),
                    "C:/Users/michael",
                )

            // then
            assertThat(path).isEqualTo(Path.of("C:/Users/michael/AppData/Local/wing-tracker/logs"))
        }

        @Test
        fun `uses a separate folder in development mode`() {
            // given / when
            val path = LogDirectory.resolve(true, "Linux", emptyMap(), "/home/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/home/michael/.local/state/wing-tracker-dev/logs"))
        }
    }
}
