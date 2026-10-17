package ch.qscqlmpa.wingtracker.ui.flights.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.settings.GetAppSettings
import ch.qscqlmpa.wingtracker.settings.SaveTrackColoring
import ch.qscqlmpa.wingtracker.settings.TrackColoring
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How the map colours the track; the pilot's choice is remembered in the settings. */
class TrackColoringPresenter(
    private val scope: CoroutineScope,
    private val getAppSettings: GetAppSettings,
    private val saveTrackColoring: SaveTrackColoring,
) {
    var coloring by mutableStateOf(TrackColoring.Flight)
        private set

    var failure by mutableStateOf<Failure?>(null)
        private set

    suspend fun load() {
        coloring = getAppSettings().trackColoring
    }

    /** Shown at once and saved on the side; a failed save only means choosing again next time. */
    fun choose(choice: TrackColoring) {
        coloring = choice
        scope.launch {
            failure =
                try {
                    saveTrackColoring(choice)
                    null
                } catch (e: CancellationException) {
                    throw e
                    // The pilot must learn the choice was not kept, but the page must stay usable.
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    Failure(FailureKind.SaveSettings, e.message)
                }
        }
    }
}
