package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.updates.InstallerKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path

class InstallationDirectoryTest {
    @Nested
    inner class Resolve {
        @Test
        fun `uses XDG config home on Linux when set`() {
            // given / when
            val env = mapOf("XDG_CONFIG_HOME" to "/home/michael/.conf")
            val path = InstallationDirectory.resolve(false, "Linux", env, "/home/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/home/michael/.conf/wing-tracker"))
        }

        @Test
        fun `falls back to the config folder on Linux`() {
            // given / when
            val path = InstallationDirectory.resolve(false, "Linux", emptyMap(), "/home/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/home/michael/.config/wing-tracker"))
        }

        @Test
        fun `uses the local, not the roaming, profile on Windows`() {
            // given / when
            val path =
                InstallationDirectory.resolve(
                    false,
                    "Windows 11",
                    mapOf("LOCALAPPDATA" to "C:/Users/michael/AppData/Local"),
                    "C:/Users/michael",
                )

            // then
            assertThat(path).isEqualTo(Path.of("C:/Users/michael/AppData/Local/wing-tracker/config"))
        }

        @Test
        fun `uses Preferences on macOS`() {
            // given / when
            val path = InstallationDirectory.resolve(false, "Mac OS X", emptyMap(), "/Users/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/Users/michael/Library/Preferences/wing-tracker"))
        }

        @Test
        fun `uses a separate folder in development mode`() {
            // given / when
            val path = InstallationDirectory.resolve(true, "Linux", emptyMap(), "/home/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/home/michael/.config/wing-tracker-dev"))
        }
    }

    @Nested
    inner class InstallerKindOf {
        @Test
        fun `is the MSI on Windows`() {
            assertThat(InstallationDirectory.installerKind("Windows 11")).isEqualTo(InstallerKind.Msi)
        }

        @Test
        fun `is the Debian package on Linux`() {
            assertThat(InstallationDirectory.installerKind("Linux")).isEqualTo(InstallerKind.Deb)
        }
    }

    @Nested
    inner class DownloadFolder {
        @Test
        fun `uses XDG cache home on Linux when set`() {
            // given / when
            val path =
                UpdateDownloadDirectory.resolve(
                    false,
                    "Linux",
                    mapOf("XDG_CACHE_HOME" to "/home/michael/.c"),
                    "/home/michael",
                )

            // then
            assertThat(path).isEqualTo(Path.of("/home/michael/.c/wing-tracker/updates"))
        }

        @Test
        fun `falls back to the cache folder on Linux`() {
            // given / when
            val path = UpdateDownloadDirectory.resolve(false, "Linux", emptyMap(), "/home/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/home/michael/.cache/wing-tracker/updates"))
        }

        @Test
        fun `uses the local profile on Windows`() {
            // given / when
            val path =
                UpdateDownloadDirectory.resolve(
                    false,
                    "Windows 11",
                    mapOf("LOCALAPPDATA" to "C:/Users/michael/AppData/Local"),
                    "C:/Users/michael",
                )

            // then
            assertThat(path).isEqualTo(Path.of("C:/Users/michael/AppData/Local/wing-tracker/cache/updates"))
        }

        @Test
        fun `uses a separate folder in development mode`() {
            // given / when
            val path = UpdateDownloadDirectory.resolve(true, "Linux", emptyMap(), "/home/michael")

            // then
            assertThat(path).isEqualTo(Path.of("/home/michael/.cache/wing-tracker-dev/updates"))
        }
    }
}
