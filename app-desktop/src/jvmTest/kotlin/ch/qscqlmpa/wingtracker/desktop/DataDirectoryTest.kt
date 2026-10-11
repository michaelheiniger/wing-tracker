package ch.qscqlmpa.wingtracker.desktop

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path

class DataDirectoryTest {
    @Nested
    inner class Resolve {
        @Test
        fun `uses XDG data home on Linux when set`() {
            // given / when
            val env = mapOf("XDG_DATA_HOME" to "/home/michael/.data")
            val path = DataDirectory.resolve(false, "Linux", env, "/home/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/home/michael/.data/wing-tracker"))
        }

        @Test
        fun `falls back to the local share folder on Linux`() {
            // given / when
            val path = DataDirectory.resolve(false, "Linux", emptyMap(), "/home/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/home/michael/.local/share/wing-tracker"))
        }

        @Test
        fun `uses Application Support on macOS`() {
            // given / when
            val path = DataDirectory.resolve(false, "Mac OS X", emptyMap(), "/Users/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/Users/michael/Library/Application Support/wing-tracker"))
        }

        @Test
        fun `uses APPDATA on Windows`() {
            // given / when
            val path =
                DataDirectory.resolve(
                    false,
                    "Windows 11",
                    mapOf("APPDATA" to "C:/Users/michael/AppData/Roaming"),
                    "C:/Users/michael",
                )

            // then
            assertThat(path).isEqualTo(Path.of("C:/Users/michael/AppData/Roaming/wing-tracker"))
        }

        @Test
        fun `uses a separate folder in development mode`() {
            // given / when
            val path = DataDirectory.resolve(true, "Linux", emptyMap(), "/home/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/home/michael/.local/share/wing-tracker-dev"))
        }
    }
}
