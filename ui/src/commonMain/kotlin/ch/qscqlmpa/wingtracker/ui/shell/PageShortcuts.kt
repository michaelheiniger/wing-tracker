package ch.qscqlmpa.wingtracker.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEvent

/**
 * Keys the page on screen reacts to wherever the focus is. Selecting text moves the focus to the shell's selection,
 * above every page, so a page's own key handler would stop hearing them; the shell asks this first instead.
 */
class PageShortcuts {
    private var handler: ((KeyEvent) -> Boolean)? = null

    /** True when the page used the key. */
    fun handle(event: KeyEvent): Boolean = handler?.invoke(event) ?: false

    internal fun register(handler: (KeyEvent) -> Boolean): () -> Unit {
        this.handler = handler
        return { if (this.handler === handler) this.handler = null }
    }
}

val LocalPageShortcuts = staticCompositionLocalOf { PageShortcuts() }

/** Lets the calling page handle keys while it is on screen. */
@Composable
fun PageShortcutsEffect(onKey: (KeyEvent) -> Boolean) {
    val shortcuts = LocalPageShortcuts.current
    val current by rememberUpdatedState(onKey)
    DisposableEffect(shortcuts) {
        val unregister = shortcuts.register { current(it) }
        onDispose { unregister() }
    }
}
