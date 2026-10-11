package ch.qscqlmpa.wingtracker.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Observable holder for the interface size, like [ThemePreference]: null follows the desktop's scaling. */
class InterfaceScalePreference(
    initial: Int? = null,
) {
    var percent: Int? by mutableStateOf(initial)

    companion object {
        /** The sizes offered next to "Automatic". */
        val CHOICES = listOf(100, 125, 150, 175, 200)
    }
}
