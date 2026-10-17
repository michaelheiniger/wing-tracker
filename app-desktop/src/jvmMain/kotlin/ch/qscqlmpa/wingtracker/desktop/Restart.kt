package ch.qscqlmpa.wingtracker.desktop

import kotlin.system.exitProcess

/** Starts the application again with the same command, so a changed data folder or an installed update takes effect. */
internal fun restartApplication(): Nothing {
    // Started before exiting: a failed start must still leave the pilot with the application closed, not half-open.
    val info = ProcessHandle.current().info()
    info.command().ifPresent { command ->
        runCatching { ProcessBuilder(listOf(command) + info.arguments().orElse(emptyArray())).inheritIO().start() }
    }
    exitProcess(0)
}
