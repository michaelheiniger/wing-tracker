package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.updates.InstallerKind
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Per-user configuration location of each OS, for what belongs to this installation rather than to the logbook, such
 * as how it updates (ADR 0024). Kept apart from the data folder, which may be shared between computers; on Windows it
 * is the local, not the roaming, profile for the same reason. Development runs have their own folder, like their data.
 */
object InstallationDirectory {
    fun resolve(
        devMode: Boolean,
        osName: String = System.getProperty("os.name"),
        env: Map<String, String> = System.getenv(),
        userHome: String = System.getProperty("user.home"),
    ): Path {
        val appFolder = if (devMode) "wing-tracker-dev" else "wing-tracker"
        val os = osName.lowercase()
        return when {
            "win" in os -> {
                Path(env["LOCALAPPDATA"] ?: "$userHome/AppData/Local").resolve(appFolder).resolve("config")
            }

            "mac" in os -> {
                Path(userHome, "Library", "Preferences", appFolder)
            }

            else -> {
                val config =
                    env["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() }?.let { Path(it) }
                        ?: Path(userHome, ".config")
                config.resolve(appFolder)
            }
        }
    }

    /** The kind of package this platform is installed from, so an update offers the same kind (ADR 0009). */
    fun installerKind(osName: String = System.getProperty("os.name")): InstallerKind =
        if ("win" in osName.lowercase()) InstallerKind.Msi else InstallerKind.Deb
}
