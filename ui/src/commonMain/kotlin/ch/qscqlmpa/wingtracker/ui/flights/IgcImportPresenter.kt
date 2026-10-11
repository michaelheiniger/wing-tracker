package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.igc.IgcImportDraft
import ch.qscqlmpa.wingtracker.flights.igc.PrepareIgcImport
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.FilePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A chosen track and what was found in it, waiting for the pilot to confirm or correct. */
class PreparedIgc(
    val file: NewAttachment,
    val draft: IgcImportDraft,
)

/** First step of an import: choose the file and read it. Nothing is saved until the pilot confirms the dialog. */
class IgcImportPresenter(
    private val scope: CoroutineScope,
    private val prepareIgcImport: PrepareIgcImport,
    private val filePicker: FilePicker,
    private val pickTitle: String,
) {
    var failure by mutableStateOf<Failure?>(null)
        private set

    fun start(onPrepared: (PreparedIgc) -> Unit) {
        scope.launch {
            try {
                failure = null
                // Cancelling the file dialog is not an error.
                val file = filePicker.pick(pickTitle, "igc").firstOrNull() ?: return@launch
                onPrepared(PreparedIgc(file, prepareIgcImport(file)))
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

    fun dismissFailure() {
        failure = null
    }
}
