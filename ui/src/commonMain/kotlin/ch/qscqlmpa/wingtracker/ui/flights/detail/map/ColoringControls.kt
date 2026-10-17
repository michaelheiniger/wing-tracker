package ch.qscqlmpa.wingtracker.ui.flights.detail.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.settings.TrackColoring
import ch.qscqlmpa.wingtracker.ui.flights.toDisplayNumber
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.map_climb_from
import ch.qscqlmpa.wingtracker.ui.resources.map_climb_range
import ch.qscqlmpa.wingtracker.ui.resources.map_climb_sink
import ch.qscqlmpa.wingtracker.ui.resources.map_coloring_climb
import ch.qscqlmpa.wingtracker.ui.resources.map_coloring_flight
import ch.qscqlmpa.wingtracker.ui.theme.MapColors
import org.jetbrains.compose.resources.stringResource

/** Both choices stay visible, like the unit switch of the statistics. */
@Composable
internal fun ColoringSwitch(
    selected: TrackColoring,
    onSelect: (TrackColoring) -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(4.dp)) {
            TrackColoring.entries.forEachIndexed { index, coloring ->
                SegmentedButton(
                    selected = coloring == selected,
                    onClick = { onSelect(coloring) },
                    shape = SegmentedButtonDefaults.itemShape(index, TrackColoring.entries.size),
                ) {
                    Text(
                        stringResource(
                            when (coloring) {
                                TrackColoring.Flight -> Res.string.map_coloring_flight
                                TrackColoring.ClimbSink -> Res.string.map_coloring_climb
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** What each colour means in metres per second, so the colouring never relies on colour alone (ADR 0019). */
@Composable
internal fun ClimbLegend(
    colors: MapColors,
    modifier: Modifier = Modifier,
) {
    val weak = WEAK_CLIMB_FROM.toDisplayNumber()
    val moderate = MODERATE_CLIMB_FROM.toDisplayNumber()
    val strong = STRONG_CLIMB_FROM.toDisplayNumber()
    val entries =
        listOf(
            ClimbBand.StrongClimb to stringResource(Res.string.map_climb_from, strong),
            ClimbBand.ModerateClimb to stringResource(Res.string.map_climb_range, moderate, strong),
            ClimbBand.WeakClimb to stringResource(Res.string.map_climb_range, weak, moderate),
            ClimbBand.Sink to stringResource(Res.string.map_climb_sink, weak),
        )
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entries.forEach { (band, text) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(width = 16.dp, height = 4.dp).background(colors.colorOf(band)))
                    Text(text, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
