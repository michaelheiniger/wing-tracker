package ch.qscqlmpa.wingtracker.desktop

import java.util.Base64

/**
 * The PowerShell step that installs an update on Windows (ADR 0025). It outlives the application: it waits until the
 * application has closed, since Windows locks the files of a running one, installs the per-user MSI showing only its
 * progress, and starts the application again, whether the install succeeded or not, so the pilot is never left
 * without it.
 */
internal object WindowsHandOff {
    fun script(
        applicationPid: Long,
        msi: String,
        log: String,
        relaunch: List<String>,
    ): String {
        // msiexec reads its own command line, so paths that may hold spaces are quoted for it, too.
        val msiArguments = listOf("/i", "\"$msi\"", "/passive", "/norestart", "/l*v", "\"$log\"")
        val start =
            relaunch.firstOrNull()?.let { command ->
                val arguments = relaunch.drop(1).map { "\"$it\"" }
                "Start-Process -FilePath ${quoted(command)}" +
                    if (arguments.isEmpty()) "" else " -ArgumentList ${array(arguments)}"
            }
        return listOfNotNull(
            "Wait-Process -Id $applicationPid -ErrorAction SilentlyContinue",
            "Start-Process -FilePath 'msiexec.exe' -ArgumentList ${array(msiArguments)} -Wait",
            start,
        ).joinToString("\n")
    }

    /** Passed encoded, so no path or argument can break out of the command line. */
    fun commandLine(script: String): List<String> =
        listOf(
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-WindowStyle",
            "Hidden",
            "-EncodedCommand",
            Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE)),
        )

    // A single-quoted PowerShell string takes everything literally except a doubled single quote.
    private fun quoted(text: String) = "'${text.replace("'", "''")}'"

    private fun array(items: List<String>) = items.joinToString(", ", "@(", ")") { quoted(it) }
}
