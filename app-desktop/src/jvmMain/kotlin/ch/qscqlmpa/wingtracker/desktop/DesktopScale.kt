package ch.qscqlmpa.wingtracker.desktop

import java.util.concurrent.TimeUnit

private const val DEFAULT_DPI = 96.0
private const val MIN_DETECTED = 1.0
private const val MAX_DETECTED = 4.0
private const val MIN_FACTOR = 0.25f
private const val PERCENT = 100.0
private const val XRDB_TIMEOUT_SECONDS = 2L

/**
 * Works out how large the interface should be. The JVM sometimes already scales itself (and does not on Linux under
 * Wayland, where X11 applications are drawn at one pixel per unit), so the detected desktop scale is only applied to
 * the extent the JVM has not applied it.
 */
object DesktopScale {
    /**
     * The scale the desktop asks applications to use, or 1.0 when nothing says otherwise. Looks at the toolkit
     * variables first and at the X11 DPI (which KDE and GNOME set for scaled screens) last.
     */
    fun detect(
        env: Map<String, String> = System.getenv(),
        xrdbOutput: () -> String? = ::queryXrdb,
    ): Double {
        val detected =
            env["QT_SCALE_FACTOR"]?.toDoubleOrNull()?.takeIf { it > 0 }
                ?: env["GDK_SCALE"]?.toDoubleOrNull()?.takeIf { it > 0 }
                ?: xftDpi(xrdbOutput())?.let { it / DEFAULT_DPI }
                ?: MIN_DETECTED
        return detected.coerceIn(MIN_DETECTED, MAX_DETECTED)
    }

    /**
     * How much to multiply the density the JVM chose. [percent] null follows the desktop but never shrinks what the
     * JVM already scaled; a fixed percent is an absolute size, 100 meaning one pixel per unit.
     */
    fun factor(
        percent: Int?,
        detected: Double,
        jvmScale: Double,
    ): Float {
        val target = percent?.let { it / PERCENT } ?: maxOf(detected, jvmScale)
        return (target / jvmScale).toFloat().coerceAtLeast(MIN_FACTOR)
    }

    private fun xftDpi(output: String?): Double? =
        output?.let {
            Regex("""Xft\.dpi:\s*([0-9]+(?:\.[0-9]+)?)""")
                .find(it)
                ?.groupValues
                ?.get(1)
                ?.toDoubleOrNull()
        }

    private fun queryXrdb(): String? =
        try {
            val process = ProcessBuilder("xrdb", "-query").redirectErrorStream(true).start()
            if (!process.waitFor(XRDB_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else {
                process.inputStream.bufferedReader().readText()
            }
        } catch (_: java.io.IOException) {
            // No X11 tools (Windows, macOS, a pure Wayland setup): nothing to detect.
            null
        }
}
