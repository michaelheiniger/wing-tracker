package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.ui.platform.InstallOutcome
import ch.qscqlmpa.wingtracker.ui.platform.UpdateInstaller
import ch.qscqlmpa.wingtracker.updates.InstallerKind
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

// pkexec's own exit codes: the pilot dismissed the password prompt, or was not allowed.
private const val PKEXEC_DISMISSED = 126
private const val PKEXEC_NOT_AUTHORIZED = 127

// Enough of the installer's output to explain a failure without pasting all of it.
private const val OUTPUT_LINES_SHOWN = 5

/** What a finished command printed (standard output and error together) and how it ended. */
class CommandResult(
    val exitCode: Int,
    val output: String,
)

/**
 * Installs an update the way the platform's package was installed (ADR 0024). On Linux the Debian package goes through
 * the package manager, run through polkit so the desktop asks for the administrator password; installing a package
 * replaces the files of the running application, which is safe there, and the new version runs after a restart.
 * Windows locks the files of a running application, so there the per-user MSI is installed while the application
 * restarts, by a step that outlives it (ADR 0025); its log goes to [logDirectory].
 */
class DesktopUpdateInstaller(
    private val kind: InstallerKind,
    private val logDirectory: Path,
    private val run: suspend (List<String>) -> CommandResult = ::runCommand,
    private val isAvailable: (String) -> Boolean = ::isOnPath,
    private val showFolder: suspend (Path) -> Unit = ::openInFileManager,
    private val startDetached: (List<String>) -> Unit = ::startDetached,
    private val restartPlainly: () -> Unit = ::restartApplication,
    private val exit: () -> Unit = { exitProcess(0) },
) : UpdateInstaller {
    // The verified MSI that waits for the restart; only ever set on Windows.
    @Volatile
    private var pendingMsi: String? = null

    override suspend fun install(path: String): InstallOutcome =
        when (kind) {
            InstallerKind.Deb -> {
                installDeb(path)
            }

            InstallerKind.Msi -> {
                pendingMsi = path
                InstallOutcome.InstallsOnRestart
            }
        }

    private suspend fun installDeb(path: String): InstallOutcome {
        if (!isAvailable("pkexec") || !isAvailable("apt-get")) {
            logger.info { "pkexec or apt-get is missing; the pilot installs $path by hand" }
            return InstallOutcome.Manual("sudo apt install '$path'")
        }
        // -y because there is no terminal to answer in; the password prompt is the pilot's confirmation.
        val result =
            try {
                run(listOf("pkexec", "apt-get", "install", "-y", path))
            } catch (e: IOException) {
                logger.warn(e) { "The installer could not be started" }
                return InstallOutcome.Failed(e.message)
            }
        logger.info { "Installing $path ended with ${result.exitCode}:\n${result.output}" }
        return when (result.exitCode) {
            0 -> {
                InstallOutcome.Installed
            }

            PKEXEC_DISMISSED -> {
                InstallOutcome.Cancelled
            }

            PKEXEC_NOT_AUTHORIZED -> {
                InstallOutcome.Failed("not authorised to install software")
            }

            else -> {
                InstallOutcome.Failed(
                    result.output
                        .lines()
                        .filter {
                            it.isNotBlank()
                        }.takeLast(OUTPUT_LINES_SHOWN)
                        .joinToString("\n"),
                )
            }
        }
    }

    override suspend fun showInFolder(path: String) = showFolder(Path.of(path).parent)

    override fun restart() {
        val msi = pendingMsi ?: return restartPlainly()
        val handOff =
            WindowsHandOff.commandLine(
                WindowsHandOff.script(
                    applicationPid = ProcessHandle.current().pid(),
                    msi = msi,
                    log = logDirectory.resolve(INSTALL_LOG).toString(),
                    relaunch = currentCommand(),
                ),
            )
        logger.info { "Closing to install $msi; the installer's log goes to ${logDirectory.resolve(INSTALL_LOG)}" }
        startDetached(handOff)
        exit()
    }
}

private const val INSTALL_LOG = "update-install.log"

/** The command that started this application, so the same one starts the installed version. */
private fun currentCommand(): List<String> {
    val info = ProcessHandle.current().info()
    return listOfNotNull(info.command().orElse(null)) + info.arguments().orElse(emptyArray())
}

// Its output is not read, so it must not wait for anyone to read it.
private fun startDetached(command: List<String>) {
    ProcessBuilder(command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
}

private suspend fun runCommand(command: List<String>): CommandResult =
    withContext(Dispatchers.IO) {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        CommandResult(process.waitFor(), output)
    }

private fun isOnPath(program: String): Boolean =
    System
        .getenv("PATH")
        .orEmpty()
        .split(File.pathSeparator)
        .any { File(it, program).canExecute() }
