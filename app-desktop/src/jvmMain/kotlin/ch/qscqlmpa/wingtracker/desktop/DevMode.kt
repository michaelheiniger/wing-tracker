package ch.qscqlmpa.wingtracker.desktop

/** Development runs are started with this system property set; see the run task in app-desktop/build.gradle.kts. */
internal object DevMode {
    const val PROPERTY = "wingtracker.dev"

    fun isOn() = System.getProperty(PROPERTY).toBoolean()
}
