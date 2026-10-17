package ch.qscqlmpa.wingtracker.ui.flights.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.igc.AnalysedTrack
import ch.qscqlmpa.wingtracker.flights.igc.ReadFlightTrack
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import kotlinx.coroutines.CancellationException

sealed interface TrackState {
    data object None : TrackState

    data object Loading : TrackState

    data class Loaded(
        val track: AnalysedTrack,
    ) : TrackState

    data class Failed(
        val failure: Failure,
    ) : TrackState
}

/** Reads the track of the flight on the page; the page calls [load] again for every flight it shows. */
class FlightTrackPresenter(
    private val readFlightTrack: ReadFlightTrack,
) {
    var state by mutableStateOf<TrackState>(TrackState.None)
        private set

    suspend fun load(flight: Flight) {
        if (flight.igcFile == null) {
            state = TrackState.None
            return
        }
        state = TrackState.Loading
        state =
            try {
                readFlightTrack(flight)?.let { TrackState.Loaded(it) } ?: TrackState.None
            } catch (e: CancellationException) {
                throw e
                // A missing or damaged file must leave the rest of the page usable.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                TrackState.Failed(Failure(FailureKind.ReadTrack, e.message))
            }
    }
}
