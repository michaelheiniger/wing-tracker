package ch.qscqlmpa.wingtracker.desktop

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Base64

private const val MSI = "C:\\Users\\michael\\AppData\\Local\\wing-tracker\\cache\\updates\\wing-tracker-0.2.0.msi"
private const val LOG = "C:\\Users\\michael\\AppData\\Local\\wing-tracker\\logs\\update-install.log"
private const val EXE = "C:\\Users\\michael\\AppData\\Local\\Programs\\wing-tracker\\wing-tracker.exe"

class WindowsHandOffTest {
    @Nested
    inner class Script {
        @Test
        fun `waits for the application, installs quietly and starts it again`() {
            // when
            val script = WindowsHandOff.script(4242, MSI, LOG, listOf(EXE))

            // then
            assertThat(script.lines()).containsExactly(
                "Wait-Process -Id 4242 -ErrorAction SilentlyContinue",
                "Start-Process -FilePath 'msiexec.exe' -ArgumentList " +
                    "@('/i', '\"$MSI\"', '/passive', '/norestart', '/l*v', '\"$LOG\"') -Wait",
                "Start-Process -FilePath '$EXE'",
            )
        }

        @Test
        fun `starts the application with the arguments it was started with`() {
            // when
            val script = WindowsHandOff.script(4242, MSI, LOG, listOf("java.exe", "-jar", "C:\\My Apps\\wt.jar"))

            // then
            assertThat(script.lines().last()).isEqualTo(
                "Start-Process -FilePath 'java.exe' -ArgumentList @('\"-jar\"', '\"C:\\My Apps\\wt.jar\"')",
            )
        }

        @Test
        fun `keeps a quote in a path from ending the string`() {
            // given
            val folder = "C:\\Users\\o'brien\\AppData\\Local\\Programs\\wing-tracker\\wing-tracker.exe"

            // when
            val script = WindowsHandOff.script(4242, MSI, LOG, listOf(folder))

            // then
            assertThat(script.lines().last()).isEqualTo("Start-Process -FilePath '${folder.replace("'", "''")}'")
        }

        @Test
        fun `still installs when the application's command is unknown`() {
            // when
            val script = WindowsHandOff.script(4242, MSI, LOG, emptyList())

            // then
            assertThat(script.lines()).hasSize(2)
        }
    }

    @Nested
    inner class CommandLine {
        @Test
        fun `passes the script encoded, hidden and without the user's profile`() {
            // given
            val script = WindowsHandOff.script(4242, MSI, LOG, listOf(EXE))

            // when
            val command = WindowsHandOff.commandLine(script)

            // then
            assertThat(command.dropLast(1)).containsExactly(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-WindowStyle",
                "Hidden",
                "-EncodedCommand",
            )
            assertThat(String(Base64.getDecoder().decode(command.last()), Charsets.UTF_16LE)).isEqualTo(script)
        }
    }
}
