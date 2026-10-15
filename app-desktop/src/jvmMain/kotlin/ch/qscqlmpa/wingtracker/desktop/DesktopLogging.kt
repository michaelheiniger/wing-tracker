package ch.qscqlmpa.wingtracker.desktop

import ch.qos.logback.core.PropertyDefinerBase
import java.nio.file.Path
import kotlin.io.path.Path

/** Where and in how much detail the desktop app logs (ADR 0023). */
internal object DesktopLogging {
    /** Lets the log go elsewhere than the platform folder, e.g. when that one is not writable. */
    const val DIRECTORY_PROPERTY = "wingtracker.log.dir"

    fun directory(devMode: Boolean = DevMode.isOn()): Path =
        System.getProperty(DIRECTORY_PROPERTY)?.takeIf { it.isNotBlank() }?.let { Path(it) }
            ?: LogDirectory.resolve(devMode)

    /** Development runs log in detail; the packaged app keeps to what helps a pilot's bug report. */
    fun level(devMode: Boolean = DevMode.isOn()) = if (devMode) "DEBUG" else "INFO"
}

// Asked by logback.xml itself, so the folder and level are right whenever the first logger appears, even one created
// while a class loads, before main runs.

internal class LogDirectoryDefiner : PropertyDefinerBase() {
    override fun getPropertyValue() = DesktopLogging.directory().toString()
}

internal class LogLevelDefiner : PropertyDefinerBase() {
    override fun getPropertyValue() = DesktopLogging.level()
}
