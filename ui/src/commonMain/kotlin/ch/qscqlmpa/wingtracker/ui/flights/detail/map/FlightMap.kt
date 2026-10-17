package ch.qscqlmpa.wingtracker.ui.flights.detail.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.flights.igc.AnalysedTrack
import ch.qscqlmpa.wingtracker.flights.igc.TrackFix
import ch.qscqlmpa.wingtracker.maps.ReadMapTile
import ch.qscqlmpa.wingtracker.maps.TileCoordinates
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.map_attribution
import ch.qscqlmpa.wingtracker.ui.resources.map_fit_flight
import ch.qscqlmpa.wingtracker.ui.resources.map_landing
import ch.qscqlmpa.wingtracker.ui.resources.map_offline
import ch.qscqlmpa.wingtracker.ui.resources.map_take_off
import ch.qscqlmpa.wingtracker.ui.theme.MapColors
import ch.qscqlmpa.wingtracker.ui.theme.extraColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import org.jetbrains.compose.resources.stringResource
import ovh.plrapps.mapcompose.api.BoundingBox
import ovh.plrapps.mapcompose.api.addLayer
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.addPath
import ovh.plrapps.mapcompose.api.disableRotation
import ovh.plrapps.mapcompose.api.hasMarker
import ovh.plrapps.mapcompose.api.moveMarker
import ovh.plrapps.mapcompose.api.removeMarker
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.api.snapScrollTo
import ovh.plrapps.mapcompose.api.updatePath
import ovh.plrapps.mapcompose.core.TileStreamProvider
import ovh.plrapps.mapcompose.ui.MapUI
import ovh.plrapps.mapcompose.ui.state.MapState

private const val TILE_SIZE = 256

// Room around the flight when the map opens, as a share of the map's size, so the markers are not cut off.
private val FIT_PADDING = Offset(0.2f, 0.2f)

private val TRACK_WIDTH = 3.dp
private val HALO_WIDTH = 6.dp

/**
 * The track on a topographic map (ADR 0027): the flight coloured by climb and sink, the recording before and after it
 * faded, with markers at take-off and landing. Without tiles, e.g. offline, the track is still drawn on a plain
 * background.
 */
@Composable
fun FlightMap(
    track: AnalysedTrack,
    readMapTile: ReadMapTile,
    // The fix the pilot points at in the altitude profile, shown as a dot on the track.
    highlighted: TrackFix?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.extraColors.map
    val takeOffText = stringResource(Res.string.map_take_off)
    val landingText = stringResource(Res.string.map_landing)
    // Tiles are fetched on the library's worker threads; a flow is safe to set from there.
    val missingTiles = remember(track) { MutableStateFlow(false) }
    val state =
        remember(track) {
            mapStateFor(track, readMapTile, missingTiles).apply {
                drawTrack(track, colors)
                drawMarkers(track, takeOffText, landingText, colors)
            }
        }
    val scope = rememberCoroutineScope()
    val focus = remember(track) { track.focusArea()?.toBoundingBox() }
    LaunchedEffect(state) { focus?.let { state.snapScrollTo(it, FIT_PADDING) } }
    LaunchedEffect(state, highlighted) { state.showHighlight(highlighted, colors) }
    val runs = remember(track) { track.climbRuns() }
    // A track without altitudes has nothing to colour by, so it keeps the one colour and has no legend.
    val byClimb = runs.isNotEmpty()
    LaunchedEffect(state) { if (byClimb) state.colorFlight(runs, colors) }
    val offline by missingTiles.collectAsState()

    Box(modifier = modifier.background(colors.background, MaterialTheme.shapes.medium)) {
        MapUI(state = state)
        if (offline) {
            StatusBadge(
                stringResource(Res.string.map_offline),
                Severity.Warning,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        }
        if (byClimb) {
            // Above the attribution, which spans the bottom of narrow maps.
            ClimbLegend(colors, Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = LEGEND_BOTTOM))
        }
        if (focus != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
            ) {
                TextButton(onClick = { scope.launch { state.scrollTo(focus, FIT_PADDING) } }) {
                    Text(stringResource(Res.string.map_fit_flight))
                }
            }
        }
        // The tiles' licence asks for it wherever they are shown.
        Text(
            stringResource(Res.string.map_attribution),
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .background(colors.trackHalo.copy(alpha = ATTRIBUTION_ALPHA))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            // On the tiles, which keep their look in dark mode too.
            color = colors.trackGround,
        )
    }
}

private fun mapStateFor(
    track: AnalysedTrack,
    readMapTile: ReadMapTile,
    missingTiles: MutableStateFlow<Boolean>,
): MapState {
    val maxZoom = readMapTile.maxZoom
    val fullSize = TILE_SIZE shl maxZoom
    val start = track.focusArea()
    return MapState(levelCount = maxZoom + 1, fullWidth = fullSize, fullHeight = fullSize, tileSize = TILE_SIZE) {
        // Beyond the most detailed tiles the map would only get blurry.
        maxScale(1.0)
        start?.let { scroll((it.left + it.right) / 2, (it.top + it.bottom) / 2) }
    }.apply {
        disableRotation()
        addLayer(
            TileStreamProvider { row, col, zoom ->
                val bytes = readMapTile(TileCoordinates(zoom, x = col, y = row))
                if (bytes == null) missingTiles.value = true
                bytes?.let { Buffer().apply { write(it) } }
            },
        )
    }
}

private fun MapState.drawTrack(
    track: AnalysedTrack,
    colors: MapColors,
) {
    val segments = track.segments()
    listOf(
        Triple("before", segments.before, colors.trackGround),
        Triple("after", segments.after, colors.trackGround),
        Triple(FLOWN, segments.flown, colors.track),
    ).forEachIndexed { index, (name, fixes, color) ->
        if (fixes.size < 2) return@forEachIndexed
        val points = fixes.map { it.toMapPoint().let { p -> p.x to p.y } }
        // The outline lies under its line, and the flight over the ground parts where they meet.
        addPath("$name-halo", width = HALO_WIDTH, color = colors.trackHalo, zIndex = index * 2f) { addPoints(points) }
        addPath(name, width = TRACK_WIDTH, color = color, zIndex = index * 2f + 1) { addPoints(points) }
    }
}

/** Lays the climb runs over the one-coloured flight, which stays only as their outline. */
private fun MapState.colorFlight(
    runs: List<ClimbRun>,
    colors: MapColors,
) {
    updatePath(FLOWN, visible = false)
    runs.forEachIndexed { index, run ->
        addPath("climb-$index", width = TRACK_WIDTH, color = colors.colorOf(run.band), zIndex = CLIMB_Z_INDEX) {
            addPoints(run.fixes.map { it.toMapPoint().let { p -> p.x to p.y } })
        }
    }
}

internal fun MapColors.colorOf(band: ClimbBand): Color =
    when (band) {
        ClimbBand.StrongSink -> strongSink
        ClimbBand.WeakSink -> weakSink
        ClimbBand.WeakClimb -> weakClimb
        ClimbBand.ModerateClimb -> moderateClimb
        ClimbBand.StrongClimb -> strongClimb
    }

private const val FLOWN = "flown"
private val LEGEND_BOTTOM = 32.dp

// Over the flight's outline and the ground parts.
private const val CLIMB_Z_INDEX = 10f

private fun MapState.drawMarkers(
    track: AnalysedTrack,
    takeOffText: String,
    landingText: String,
    colors: MapColors,
) {
    val flown = track.flown.ifEmpty { return }
    addPlace("take-off", flown.first(), takeOffText, colors.track)
    addPlace("landing", flown.last(), landingText, colors.track)
}

private fun MapState.addPlace(
    id: String,
    fix: TrackFix,
    text: String,
    tint: Color,
) {
    val point = fix.toMapPoint()
    addMarker(id, point.x, point.y, clickable = false) { PlaceMarker(text, tint) }
}

/** Icon and text, so take-off and landing are told apart without relying on colour (ADR 0019). */
@Composable
private fun PlaceMarker(
    text: String,
    tint: Color,
) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Default.Place, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun MapState.showHighlight(
    fix: TrackFix?,
    colors: MapColors,
) {
    if (fix == null) {
        removeMarker(HIGHLIGHT)
        return
    }
    val point = fix.toMapPoint()
    if (hasMarker(HIGHLIGHT)) {
        moveMarker(HIGHLIGHT, point.x, point.y)
    } else {
        // Centred on the fix, and above the take-off and landing markers it may pass.
        addMarker(HIGHLIGHT, point.x, point.y, relativeOffset = CENTRED, zIndex = 1f, clickable = false) {
            HighlightDot(colors)
        }
    }
}

@Composable
private fun HighlightDot(colors: MapColors) {
    Box(
        Modifier
            .size(HIGHLIGHT_SIZE)
            .background(colors.trackHalo, CircleShape)
            .padding(HIGHLIGHT_RING)
            .background(colors.track, CircleShape),
    )
}

private const val HIGHLIGHT = "highlight"
private val CENTRED = Offset(-0.5f, -0.5f)
private val HIGHLIGHT_SIZE = 14.dp
private val HIGHLIGHT_RING = 3.dp

private fun MapArea.toBoundingBox() = BoundingBox(xLeft = left, yTop = top, xRight = right, yBottom = bottom)

private const val ATTRIBUTION_ALPHA = 0.8f
