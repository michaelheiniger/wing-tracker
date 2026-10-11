package ch.qscqlmpa.wingtracker.desktop

import java.util.concurrent.TimeUnit

private const val COMMAND_TIMEOUT_SECONDS = 2L
private const val PORTAL_PREFERS_DARK = "1"
private const val PORTAL_PREFERS_LIGHT = "2"

/**
 * Whether the desktop is set to a dark appearance. The JVM's own detection misses KDE Plasma, so this asks, in
 * order, the freedesktop portal (understood by KDE and GNOME), GNOME's settings and the KDE colour scheme name.
 * Null means no source gave an answer, so the caller keeps its own default.
 */
class SystemTheme(
    private val run: (List<String>) -> String? = ::runCommand,
) {
    fun isDark(): Boolean? = portal() ?: gsettings() ?: kdeColorScheme()

    private fun portal(): Boolean? =
        run(
            listOf(
                "busctl",
                "--user",
                "call",
                "org.freedesktop.portal.Desktop",
                "/org/freedesktop/portal/desktop",
                "org.freedesktop.portal.Settings",
                "Read",
                "ss",
                "org.freedesktop.appearance",
                "color-scheme",
            ),
        )?.let { parsePortal(it) }

    private fun gsettings(): Boolean? =
        run(listOf("gsettings", "get", "org.gnome.desktop.interface", "color-scheme"))?.let { parseGsettings(it) }

    private fun kdeColorScheme(): Boolean? =
        run(listOf("kreadconfig6", "--file", "kdeglobals", "--group", "General", "--key", "ColorScheme"))
            ?.let { parseKdeColorScheme(it) }

    companion object {
        // The portal answers e.g. "v v u 1": 1 is dark, 2 is light, 0 means no preference.
        fun parsePortal(output: String): Boolean? =
            when (output.trim().split(Regex("\\s+")).lastOrNull()) {
                PORTAL_PREFERS_DARK -> true
                PORTAL_PREFERS_LIGHT -> false
                else -> null
            }

        // GNOME answers 'prefer-dark', 'prefer-light' or 'default'; only the first two are an opinion.
        fun parseGsettings(output: String): Boolean? =
            when (output.trim().trim('\'')) {
                "prefer-dark" -> true
                "prefer-light" -> false
                else -> null
            }

        // KDE colour schemes are named by their authors; "BreezeDark" and friends contain the word.
        fun parseKdeColorScheme(output: String): Boolean? =
            output.trim().takeIf { it.isNotEmpty() }?.contains("dark", ignoreCase = true)
    }
}

private fun runCommand(command: List<String>): String? =
    runCatching {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (finished && process.exitValue() == 0) text else null
    }.getOrNull()
