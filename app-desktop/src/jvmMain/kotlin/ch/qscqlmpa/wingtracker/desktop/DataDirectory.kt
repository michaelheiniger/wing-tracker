package ch.qscqlmpa.wingtracker.desktop

import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Per-user application data location of each OS, so data survives upgrades and follows platform conventions.
 * Development runs use a separate folder so experiments never touch the pilot's real logbook.
 */
object DataDirectory {
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
