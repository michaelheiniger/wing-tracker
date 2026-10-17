package ch.qscqlmpa.wingtracker.ui.flights.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.flight_altitude
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_best_climb
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_landing_altitude
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_landing_time
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_max_altitude
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_max_sink
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_no_flight_in_track
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_section_track
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_takeoff_altitude
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_takeoff_time
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_vertical_speed
import ch.qscqlmpa.wingtracker.ui.toDisplay
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/** What the track says, kept apart from what is stored about the flight, so the pilot sees where each comes from. */
@Composable
internal fun TrackFacts(track: TrackState) {
    if (track == TrackState.None) return
    FactGroup(stringResource(Res.string.flight_detail_section_track)) {
        when (track) {
            TrackState.None -> {}

            TrackState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }

            is TrackState.Failed -> {
                Text(track.failure.message(), color = MaterialTheme.colorScheme.error)
            }

            is TrackState.Loaded -> {
                val statistics = track.track.statistics
                if (statistics == null) {
                    Text(
                        stringResource(Res.string.flight_detail_no_flight_in_track),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Fact(Res.string.flight_detail_takeoff_time, statistics.takeOffTime.toDisplayWithSeconds())
                    Fact(Res.string.flight_detail_landing_time, statistics.landingTime.toDisplayWithSeconds())
                    Fact(
                        Res.string.flight_detail_takeoff_altitude,
                        statistics.takeOffAltitudeMeters?.let { meters(it) },
                    )
                    Fact(
                        Res.string.flight_detail_landing_altitude,
                        statistics.landingAltitudeMeters?.let { meters(it) },
                    )
                    Fact(Res.string.flight_detail_max_altitude, statistics.maxAltitudeMeters?.let { meters(it) })
                    Fact(
                        Res.string.flight_detail_best_climb,
                        statistics.bestClimbMetersPerSecond?.let { verticalSpeed(it) },
                    )
                    Fact(
                        Res.string.flight_detail_max_sink,
                        statistics.maxSinkMetersPerSecond?.let { verticalSpeed(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun meters(value: Int) = stringResource(Res.string.flight_altitude, value)

/** Signed and to a tenth, like a vario shows it. */
@Composable
internal fun verticalSpeed(metersPerSecond: Double): String {
    val tenths = (metersPerSecond * TENTHS).roundToInt()
    val sign =
        when {
            tenths > 0 -> "+"
            tenths < 0 -> "−"
            else -> ""
        }
    val digits = "${tenths.absoluteValue / TENTHS}.${tenths.absoluteValue % TENTHS}"
    return stringResource(Res.string.flight_detail_vertical_speed, sign + digits)
}

private fun LocalTime.toDisplayWithSeconds() = "${toDisplay()}:${second.toString().padStart(2, '0')}"

private const val TENTHS = 10
