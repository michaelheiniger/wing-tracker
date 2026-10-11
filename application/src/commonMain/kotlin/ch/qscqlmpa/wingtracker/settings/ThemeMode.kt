package ch.qscqlmpa.wingtracker.settings

/** User's theme choice; [System] defers to the OS so the app matches the rest of the desktop by default. */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    fun isDark(systemInDarkTheme: Boolean): Boolean =
        when (this) {
            System -> systemInDarkTheme
            Light -> false
            Dark -> true
        }
}
