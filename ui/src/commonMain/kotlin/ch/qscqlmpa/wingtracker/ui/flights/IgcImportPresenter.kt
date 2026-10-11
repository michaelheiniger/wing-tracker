package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.FilePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Entry of an import: collects the tracks, from the file chooser or from files dropped on the logbook. */
class IgcImportPresenter(
    private val scope: CoroutineScope,
    private val filePicker: FilePicker,
    private val pickTitle: String,
) {
    var failure by mutableStateOf<Failure?>(null)
        private set

    fun choose(onChosen: (List<NewAttachment>) -> Unit) {
        scope.launch {
            try {
                failure = null
                // Cancelling the file dialog is not an error.
                val files = filePicker.pick(pickTitle, IGC_EXTENSION)
                if (files.isNotEmpty()) onChosen(files)
            } catch (e: CancellationException) {
                throw e
                // A file that cannot be read must show a message, never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = Failure(FailureKind.ImportIgc, e.message)
            }
        }
    }

    /** Dropping something that is not a track is reported instead of silently doing nothing. */
    fun dropRejected() {
        failure = Failure(FailureKind.DropNoIgc, null)
    }

    fun dismissFailure() {
        failure = null
    }
}

const val IGC_EXTENSION = "igc"
