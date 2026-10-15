package ch.qscqlmpa.wingtracker.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import ch.qscqlmpa.wingtracker.ui.errors.UnexpectedError
import ch.qscqlmpa.wingtracker.ui.errors.UnexpectedErrors
import kotlinx.coroutines.CoroutineExceptionHandler
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.SwingUtilities

/**
 * Sends every exception nothing else handled to [errors], so the pilot sees it instead of the app dying or
 * failing silently.
 */
internal class UnexpectedErrorHandling(
    val errors: UnexpectedErrors,
) {
    /** Changes when the main window must be built again; its content keys on it. */
    var windowGeneration by mutableIntStateOf(0)
        private set

    // A failure inside the window can leave its composition dead, so the window is rebuilt once the pilot has seen it.
    private var windowBroken = false

    /** Catches failures on every thread outside the main window: background work and plain Swing callbacks. */
    fun install() {
        Thread.setDefaultUncaughtExceptionHandler { _, error -> SwingUtilities.invokeLater { report(error) } }
    }

    /**
     * For coroutines outside the main window, which would otherwise end the app when they fail. Only a root
     * coroutine or a direct child of a supervisor uses it.
     */
    val coroutineExceptionHandler =
        CoroutineExceptionHandler {
            _,
            error,
            ->
            SwingUtilities.invokeLater { report(error) }
        }

    /**
     * Catches failures inside a window: composition, layout, drawing, input and the window's coroutines. Not
     * rethrown, otherwise Compose closes the window and the thread handler reports the failure a second time.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    val windowExceptionHandlerFactory =
        WindowExceptionHandlerFactory {
            // Compose calls it on the UI thread, where errors expects its reports.
            WindowExceptionHandler { error ->
                windowBroken = true
                report(error)
            }
        }

    private fun report(error: Throwable) {
        // Still printed as before, so it reaches the console and the system log even after the dialog is closed;
        // once only, as a failure while drawing repeats on every frame.
        if (errors.report(error).occurrences == 1) System.err.println(error.stackTraceToString())
    }

    fun dismiss() {
        errors.dismiss()
        if (errors.current == null && windowBroken) {
            windowBroken = false
            windowGeneration++
        }
    }

    fun copy(error: UnexpectedError) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(error.stackTrace), null)
    }
}
