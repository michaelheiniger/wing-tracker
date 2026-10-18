package ch.qscqlmpa.wingtracker.ui.flights.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.flights.SiteNames
import ch.qscqlmpa.wingtracker.flights.SiteSource
import ch.qscqlmpa.wingtracker.flights.igc.TrackStatistics
import ch.qscqlmpa.wingtracker.maps.GeoPoint
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.flights.label
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_site_other
import ch.qscqlmpa.wingtracker.ui.resources.site_source_track
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

/** A site's shown name with where it came from, and below it what the other sources call it (ADR 0030). */
@Composable
internal fun SiteFact(
    label: StringResource,
    names: SiteNames,
) {
    Fact(label, names.shown)
    val shownSource = names.shownSource ?: return
    Column(
        modifier = Modifier.padding(start = FACT_VALUE_INDENT),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatusBadge(shownSource.label(), Severity.Neutral)
        SiteSource.entries.filter { it != shownSource }.forEach { source ->
            names[source]?.let { name ->
                Text(
                    stringResource(Res.string.flight_detail_site_other, source.label(), name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Where the track says the flight took off or landed; shown only once the track is read, as it is never stored. */
@Composable
internal fun TrackPositionFact(
    label: StringResource,
    track: TrackState,
    position: (TrackStatistics) -> GeoPoint,
) {
    val statistics = (track as? TrackState.Loaded)?.track?.statistics ?: return
    Fact(label, position(statistics).toDisplay())
    StatusBadge(
        stringResource(Res.string.site_source_track),
        Severity.Neutral,
        Modifier.padding(start = FACT_VALUE_INDENT),
    )
}

/**
 * Signed decimal degrees, latitude first, as map sites take them when pasted. Five decimals place a point to about a
 * metre, which is all a logger can tell.
 */
internal fun GeoPoint.toDisplay(): String = "${degrees(latitude)}, ${degrees(longitude)}"

private fun degrees(value: Double): String {
    val scaled = (value * POSITION_SCALE).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val whole = scaled.absoluteValue / POSITION_SCALE
    val fraction = (scaled.absoluteValue % POSITION_SCALE).toString().padStart(POSITION_DECIMALS, '0')
    return "$sign$whole.$fraction"
}

private const val POSITION_DECIMALS = 5
private const val POSITION_SCALE = 100_000L
