package ch.qscqlmpa.wingtracker.desktop

/** Development runs are started with this system property set; see the run task in app-desktop/build.gradle.kts. */
internal object DevMode {
    const val PROPERTY = "wingtracker.dev"

    fun isOn() = System.getProperty(PROPERTY).toBoolean()

    /**
     * The name the app's folders and keyring entries go by. A development run has its own, so trying something out
     * never touches the pilot's real data, logs, installation or keys.
     */
    fun appName(devMode: Boolean) = if (devMode) "wing-tracker-dev" else "wing-tracker"
}
