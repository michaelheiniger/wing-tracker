package ch.qscqlmpa.wingtracker.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.LogLocation
import ch.qscqlmpa.wingtracker.ui.toFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Drives the log section of the settings: show and open the folder the log is written to. */
class LogLocationPresenter(
    private val scope: CoroutineScope,
    private val location: LogLocation,
) {
    val path: String get() = location.path

    var failure by mutableStateOf<Failure?>(null)
        private set

    fun open() {
        scope.launch {
            try {
                failure = null
                location.open()
            } catch (e: CancellationException) {
                throw e
                // A file-manager problem must show a message, never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = e.toFailure(FailureKind.OpenLogFolder)
            }
        }
    }
}
