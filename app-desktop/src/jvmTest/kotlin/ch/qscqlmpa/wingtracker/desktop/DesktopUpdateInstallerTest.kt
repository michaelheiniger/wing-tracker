package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.ui.platform.InstallOutcome
import ch.qscqlmpa.wingtracker.updates.InstallerKind
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Path

private const val LOCK_ERROR =
    "E: Could not get lock /var/lib/dpkg/lock-frontend. It is held by process 4242 (unattended-upgr)"
private const val MSI = "C:\\Users\\michael\\AppData\\Local\\wing-tracker\\cache\\updates\\wing-tracker-0.2.0.msi"
private const val CACHED = "/home/michael/.cache/wing-tracker/updates/wing-tracker_0.2.0_amd64.deb"

class DesktopUpdateInstallerTest {
    private val commands = mutableListOf<List<String>>()
    private val detached = mutableListOf<List<String>>()
    private var restartedPlainly = false
    private var exited = false

    private fun installer(
        kind: InstallerKind = InstallerKind.Deb,
        available: Set<String> = setOf("pkexec", "apt-get"),
        result: () -> CommandResult = { CommandResult(0, "Setting up wing-tracker (0.2.0) ...") },
    ) = DesktopUpdateInstaller(
        kind,
        Path.of("C:/Users/michael/AppData/Local/wing-tracker/logs"),
        run = { command ->
            commands += command
            result()
        },
        isAvailable = { it in available },
        showFolder = {},
        startDetached = { detached += it },
        restartPlainly = { restartedPlainly = true },
        exit = { exited = true },
    )

    @Nested
    inner class Install {
        @Test
        fun `installs the Debian package through the package manager, asking for the password`() =
            runTest {
                // when
                val outcome = installer().install(CACHED)

                // then
                assertThat(outcome).isEqualTo(InstallOutcome.Installed)
                assertThat(commands).containsExactly(listOf("pkexec", "apt-get", "install", "-y", CACHED))
            }

        @Test
        fun `reports a dismissed password prompt as cancelled`() =
            runTest {
                // when
                val outcome =
                    installer(result = {
                        CommandResult(126, "Error executing command as another user: Request dismissed")
                    }).install(CACHED)

                // then
                assertThat(outcome).isEqualTo(InstallOutcome.Cancelled)
            }

        @Test
        fun `reports a pilot who may not install software`() =
            runTest {
                // when
                val outcome =
                    installer(result = {
                        CommandResult(127, "Error executing command as another user: Not authorized")
                    }).install(CACHED)

                // then
                assertThat(outcome).isEqualTo(InstallOutcome.Failed("not authorised to install software"))
            }

        @Test
        fun `reports the last lines the package manager printed when it failed`() =
            runTest {
                // given
                val output =
                    "Reading package lists...\nBuilding dependency tree...\n\n" +
                        "$LOCK_ERROR\n"

                // when
                val outcome = installer(result = { CommandResult(100, output) }).install(CACHED)

                // then
                assertThat(outcome).isEqualTo(
                    InstallOutcome.Failed(
                        "Reading package lists...\nBuilding dependency tree...\n$LOCK_ERROR",
                    ),
                )
            }

        @Test
        fun `reports an installer that cannot be started`() =
            runTest {
                // when
                val outcome = installer(result = { throw IOException("Cannot run program \"pkexec\"") }).install(CACHED)

                // then
                assertThat(outcome).isEqualTo(InstallOutcome.Failed("Cannot run program \"pkexec\""))
            }

        @Test
        fun `gives the command to install by hand when polkit or apt is missing`() =
            runTest {
                // when
                val outcome = installer(available = setOf("apt-get")).install(CACHED)

                // then
                assertThat(outcome).isEqualTo(InstallOutcome.Manual("sudo apt install '$CACHED'"))
                assertThat(commands).isEmpty()
            }

        @Test
        fun `waits for the restart on Windows, which cannot replace a running application`() =
            runTest {
                // when
                val outcome = installer(kind = InstallerKind.Msi).install(MSI)

                // then
                assertThat(outcome).isEqualTo(InstallOutcome.InstallsOnRestart)
                assertThat(commands).isEmpty()
                assertThat(detached).isEmpty()
            }
    }

    @Nested
    inner class Restart {
        @Test
        fun `simply restarts after installing on Linux`() =
            runTest {
                // given
                val installer = installer()
                installer.install(CACHED)

                // when
                installer.restart()

                // then
                assertThat(restartedPlainly).isTrue()
                assertThat(detached).isEmpty()
            }

        @Test
        fun `hands the waiting installer to PowerShell and closes on Windows`() =
            runTest {
                // given
                val installer = installer(kind = InstallerKind.Msi)
                installer.install(MSI)

                // when
                installer.restart()

                // then
                assertThat(detached.single()).startsWith("powershell.exe", "-NoProfile")
                assertThat(exited).isTrue()
                assertThat(restartedPlainly).isFalse()
            }

        @Test
        fun `simply restarts on Windows when nothing waits to be installed`() =
            runTest {
                // when
                installer(kind = InstallerKind.Msi).restart()

                // then
                assertThat(restartedPlainly).isTrue()
                assertThat(detached).isEmpty()
            }
    }
}
