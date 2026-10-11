package ch.qscqlmpa.wingtracker.desktop

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Per-user application data location of each OS, so data survives upgrades and follows platform conventions.
 * Development runs use a separate folder so experiments never touch the pilot's real logbook.
 */
object DataDirectory {
    /**
     * The folder in use: the one the pilot chose in Settings, otherwise the platform default. A chosen folder that
     * no longer exists (e.g. an unplugged drive) is ignored, so the app never quietly starts an empty logbook there.
     */
    fun configured(
        devMode: Boolean,
        osName: String = System.getProperty("os.name"),
        env: Map<String, String> = System.getenv(),
        userHome: String = System.getProperty("user.home"),
    ): Path {
        val chosen = readChosen(pointerFile(devMode, osName, env, userHome))
        return chosen?.takeIf { it.isDirectory() } ?: resolve(devMode, osName, env, userHome)
    }

    /**
     * Where the chosen folder is remembered: next to the default folder, because it cannot live inside the folder
     * it points to. Development runs have their own, so they never follow the real logbook.
     */
    fun pointerFile(
        devMode: Boolean,
        osName: String = System.getProperty("os.name"),
        env: Map<String, String> = System.getenv(),
        userHome: String = System.getProperty("user.home"),
    ): Path {
        val default = resolve(devMode, osName, env, userHome)
        return default.resolveSibling("${default.name}.location")
    }

    private fun readChosen(pointer: Path): Path? =
        runCatching {
            pointer
                .readText()
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { Path(it) }
        }.getOrNull()

    /** The platform default, whatever was chosen. */
    fun resolve(
        devMode: Boolean,
        osName: String = System.getProperty("os.name"),
        env: Map<String, String> = System.getenv(),
        userHome: String = System.getProperty("user.home"),
    ): Path {
        val appFolder = if (devMode) "wing-tracker-dev" else "wing-tracker"
        val os = osName.lowercase()
        val base =
            when {
                "win" in os -> {
                    Path(env["APPDATA"] ?: "$userHome/AppData/Roaming")
                }

                "mac" in os -> {
                    Path(userHome, "Library", "Application Support")
                }

                else -> {
                    env["XDG_DATA_HOME"]?.takeIf { it.isNotBlank() }?.let { Path(it) }
                        ?: Path(userHome, ".local", "share")
                }
            }
        return base.resolve(appFolder)
    }
}
