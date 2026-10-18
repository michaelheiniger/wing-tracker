package ch.qscqlmpa.wingtracker.desktop

import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Per-user log location of each OS. Kept apart from the data folder, so logs never end up in a synchronised or
 * backed-up logbook and are found even when the chosen data folder is missing (ADR 0023). Development runs have their
 * own folder, like their data.
 */
object LogDirectory {
    fun resolve(
        devMode: Boolean,
        osName: String = System.getProperty("os.name"),
        env: Map<String, String> = System.getenv(),
        userHome: String = System.getProperty("user.home"),
    ): Path {
        val appFolder = DevMode.appName(devMode)
        val os = osName.lowercase()
        return when {
            "win" in os -> {
                Path(env["LOCALAPPDATA"] ?: "$userHome/AppData/Local").resolve(appFolder).resolve("logs")
            }

            "mac" in os -> {
                Path(userHome, "Library", "Logs", appFolder)
            }

            else -> {
                val state =
                    env["XDG_STATE_HOME"]?.takeIf { it.isNotBlank() }?.let { Path(it) }
                        ?: Path(userHome, ".local", "state")
                state.resolve(appFolder).resolve("logs")
            }
        }
    }
}
