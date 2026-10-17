package ch.qscqlmpa.wingtracker.ui.flights.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.igc.TrackFix
import ch.qscqlmpa.wingtracker.flights.syride.SYRIDE_SYSTEM
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.components.AppCard
import ch.qscqlmpa.wingtracker.ui.flights.FlightNeighbours
import ch.qscqlmpa.wingtracker.ui.flights.detail.map.FlightMap
import ch.qscqlmpa.wingtracker.ui.flights.detail.map.MapSetup
import ch.qscqlmpa.wingtracker.ui.flights.detail.profile.AltitudeProfile
import ch.qscqlmpa.wingtracker.ui.flights.toDisplayNumber
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.flight_altitude
import ch.qscqlmpa.wingtracker.ui.resources.flight_date_time
import ch.qscqlmpa.wingtracker.ui.resources.flight_delete
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_back
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_date
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_distance
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_duration
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_earlier
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_imported_from
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_instrument
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_landing_site
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_later
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_logged_at
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_missing
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_no_notes
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_open_syride
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_open_track
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_section_flight
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_section_notes
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_section_origin
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_section_sites
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_section_tracking
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_syride
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_takeoff_site
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_takeoff_time
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_top_altitude
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_track_file
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_wing
import ch.qscqlmpa.wingtracker.ui.resources.flight_distance
import ch.qscqlmpa.wingtracker.ui.resources.flight_edit
import ch.qscqlmpa.wingtracker.ui.toDisplay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Everything known about one flight. Esc goes back to the list, and the arrow keys step to the earlier or later
 * flight of the current search, so a pilot can go through a series of flights without the mouse. A flight with a
 * track shows its map beside the data, which scroll on their own, so scrolling the page never zooms the map.
 */
@Composable
fun FlightDetailScreen(
    flight: Flight,
    wingName: String,
    neighbours: FlightNeighbours,
    track: TrackState,
    mapSetup: MapSetup,
    failure: Failure?,
    actions: FlightDetailActions,
) {
    val focus = remember { FocusRequester() }
    // Taken again for every flight, since the buttons that stepped here may hold the focus.
    LaunchedEffect(flight.id) { focus.requestFocus() }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val target =
                        when (event.key) {
                            Key.Escape -> return@onKeyEvent true.also { actions.onBack() }
                            Key.DirectionLeft -> neighbours.earlier
                            Key.DirectionRight -> neighbours.later
                            else -> return@onKeyEvent false
                        }
                    target?.let(actions.onOpen)
                    target != null
                }.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(flight, wingName, neighbours, actions)
        failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
        if (flight.igcFile == null) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { Facts(flight, wingName, track) }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TrackView(track, mapSetup, Modifier.weight(1f).fillMaxHeight())
                Column(
                    modifier = Modifier.width(FACT_GROUP_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { Facts(flight, wingName, track) }
            }
        }
    }
}

/** The map once the track is read; the facts beside it explain when it cannot be. */
@Composable
private fun TrackView(
    track: TrackState,
    mapSetup: MapSetup,
    modifier: Modifier,
) {
    when (track) {
        is TrackState.Loaded -> {
            // Shared by the profile and the map, so pointing at one shows the place on the other.
            var highlighted by remember(track) { mutableStateOf<TrackFix?>(null) }
            Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlightMap(track.track, mapSetup, highlighted, Modifier.fillMaxWidth().weight(1f))
                AltitudeProfile(
                    track.track,
                    highlighted,
                    onHighlight = { highlighted = it },
                    Modifier.fillMaxWidth().height(PROFILE_HEIGHT),
                )
            }
        }

        TrackState.Loading -> {
            Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        is TrackState.Failed, TrackState.None -> {
            Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium))
        }
    }
}

@Composable
private fun Header(
    flight: Flight,
    wingName: String,
    neighbours: FlightNeighbours,
    actions: FlightDetailActions,
) {
    val details = flight.details
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = actions.onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text(stringResource(Res.string.flight_detail_back), modifier = Modifier.padding(start = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepButton(neighbours.earlier, actions.onOpen) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                Text(stringResource(Res.string.flight_detail_earlier))
            }
            StepButton(neighbours.later, actions.onOpen) {
                Text(stringResource(Res.string.flight_detail_later))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val time = details.takeOffTime
        Text(
            if (time == null) {
                details.date.toDisplay()
            } else {
                stringResource(Res.string.flight_date_time, details.date.toDisplay(), time.toDisplay())
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            "$wingName · ${formatDuration(details.duration)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = actions.onEdit) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Text(stringResource(Res.string.flight_edit), modifier = Modifier.padding(start = 8.dp))
        }
        if (flight.igcFile != null) {
            OutlinedButton(onClick = actions.onOpenTrack) {
                Text(stringResource(Res.string.flight_detail_open_track))
            }
        }
        actions.onOpenSyride?.let { open ->
            OutlinedButton(onClick = open) { Text(stringResource(Res.string.flight_detail_open_syride)) }
        }
        OutlinedButton(onClick = actions.onDelete) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Text(stringResource(Res.string.flight_delete), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Disabled at either end of the list, so the pilot sees there is nothing further in that direction. */
@Composable
private fun StepButton(
    target: Flight?,
    onOpen: (Flight) -> Unit,
    content: @Composable () -> Unit,
) {
    TextButton(onClick = { target?.let(onOpen) }, enabled = target != null) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(
    flight: Flight,
    wingName: String,
    track: TrackState,
) {
    val details = flight.details
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FactGroup(stringResource(Res.string.flight_detail_section_flight)) {
            Fact(Res.string.flight_detail_date, details.date.toDisplay())
            Fact(Res.string.flight_detail_takeoff_time, details.takeOffTime?.toDisplay())
            Fact(Res.string.flight_detail_duration, formatDuration(details.duration))
            Fact(Res.string.flight_detail_wing, wingName)
        }
        FactGroup(stringResource(Res.string.flight_detail_section_sites)) {
            Fact(Res.string.flight_detail_takeoff_site, details.takeOffSite)
            Fact(Res.string.flight_detail_landing_site, details.landingSite)
        }
        FactGroup(stringResource(Res.string.flight_detail_section_tracking)) {
            Fact(
                Res.string.flight_detail_distance,
                details.distanceKm?.let { stringResource(Res.string.flight_distance, it.toDisplayNumber()) },
            )
            Fact(
                Res.string.flight_detail_top_altitude,
                details.topAltitudeMeters?.let { stringResource(Res.string.flight_altitude, it) },
            )
            Fact(Res.string.flight_detail_instrument, details.instrument)
        }
        TrackFacts(track)
        FactGroup(stringResource(Res.string.flight_detail_section_origin)) {
            if (flight.importedFrom?.system == SYRIDE_SYSTEM) {
                Fact(Res.string.flight_detail_imported_from, stringResource(Res.string.flight_detail_syride))
            }
            Fact(Res.string.flight_detail_track_file, flight.igcFile?.attachment?.fileName)
            val logged = flight.loggedAt.toLocalDateTime(TimeZone.currentSystemDefault())
            Fact(
                Res.string.flight_detail_logged_at,
                stringResource(Res.string.flight_date_time, logged.date.toDisplay(), logged.time.toDisplay()),
            )
        }
    }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.flight_detail_section_notes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            val notes = details.notes
            if (notes == null) {
                Text(
                    stringResource(Res.string.flight_detail_no_notes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(notes)
            }
        }
    }
}

@Composable
internal fun FactGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    AppCard(modifier = Modifier.width(FACT_GROUP_WIDTH)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** Every field is listed, also an empty one, so the pilot sees what is missing and can add it by editing. */
@Composable
internal fun Fact(
    label: StringResource,
    value: String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(label),
            modifier = Modifier.width(LABEL_WIDTH),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (value == null) {
            Text(stringResource(Res.string.flight_detail_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(value)
        }
    }
}

private val FACT_GROUP_WIDTH = 340.dp
private val LABEL_WIDTH = 150.dp
private val PROFILE_HEIGHT = 200.dp
