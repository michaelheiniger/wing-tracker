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
        val appFolder = DevMode.appName(devMode)
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

/**
 * Per-user cache location of each OS, for what this installation can fetch again: a downloaded update waiting to be
 * installed (ADR 0024) and map tiles (ADR 0027). Development runs have their own folder.
 */
object CacheDirectory {
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
                Path(env["LOCALAPPDATA"] ?: "$userHome/AppData/Local").resolve(appFolder).resolve("cache")
            }

            "mac" in os -> {
                Path(userHome, "Library", "Caches", appFolder)
            }

            else -> {
                val cache =
                    env["XDG_CACHE_HOME"]?.takeIf { it.isNotBlank() }?.let { Path(it) }
                        ?: Path(userHome, ".cache")
                cache.resolve(appFolder)
            }
        }
    }
}

/** Where a downloaded update waits to be installed; losing it costs only a new download (ADR 0024). */
object UpdateDownloadDirectory {
    fun resolve(
        devMode: Boolean,
        osName: String = System.getProperty("os.name"),
        env: Map<String, String> = System.getenv(),
        userHome: String = System.getProperty("user.home"),
    ): Path = CacheDirectory.resolve(devMode, osName, env, userHome).resolve("updates")
}

/** Where fetched map tiles are kept, so a flight viewed before shows its map offline too (ADR 0027). */
object MapTileDirectory {
    fun resolve(
        devMode: Boolean,
        osName: String = System.getProperty("os.name"),
        env: Map<String, String> = System.getenv(),
        userHome: String = System.getProperty("user.home"),
    ): Path = CacheDirectory.resolve(devMode, osName, env, userHome).resolve("map-tiles")
}
