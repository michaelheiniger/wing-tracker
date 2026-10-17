package ch.qscqlmpa.wingtracker.ui.flights.detail.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.flights.igc.AnalysedTrack
import ch.qscqlmpa.wingtracker.flights.igc.GroundProfile
import ch.qscqlmpa.wingtracker.flights.igc.TrackFix
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.flights.detail.map.segments
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.flight_altitude
import ch.qscqlmpa.wingtracker.ui.resources.profile_altitude
import ch.qscqlmpa.wingtracker.ui.resources.profile_description
import ch.qscqlmpa.wingtracker.ui.resources.profile_ground_above
import ch.qscqlmpa.wingtracker.ui.resources.profile_ground_incomplete
import ch.qscqlmpa.wingtracker.ui.resources.profile_ground_source
import ch.qscqlmpa.wingtracker.ui.resources.profile_legend_flight
import ch.qscqlmpa.wingtracker.ui.resources.profile_legend_outside_flight
import ch.qscqlmpa.wingtracker.ui.resources.profile_legend_terrain
import ch.qscqlmpa.wingtracker.ui.statistics.AxisScale
import ch.qscqlmpa.wingtracker.ui.statistics.ChartTheme
import ch.qscqlmpa.wingtracker.ui.statistics.ChartTooltip
import ch.qscqlmpa.wingtracker.ui.statistics.LegendDot
import ch.qscqlmpa.wingtracker.ui.statistics.PlotArea
import ch.qscqlmpa.wingtracker.ui.statistics.TOOLTIP_WIDTH
import ch.qscqlmpa.wingtracker.ui.statistics.TooltipRow
import ch.qscqlmpa.wingtracker.ui.statistics.drawRingedDot
import ch.qscqlmpa.wingtracker.ui.statistics.drawValueAxis
import ch.qscqlmpa.wingtracker.ui.statistics.niceScale
import ch.qscqlmpa.wingtracker.ui.statistics.rememberPlotArea
import ch.qscqlmpa.wingtracker.ui.statistics.tooltipOffset
import ch.qscqlmpa.wingtracker.ui.statistics.trackPointer
import ch.qscqlmpa.wingtracker.ui.theme.LocalDarkTheme
import ch.qscqlmpa.wingtracker.ui.theme.extraColors
import ch.qscqlmpa.wingtracker.ui.toDisplay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private const val MAX_TIME_TICKS = 6
private const val FLIGHT_FILL_ALPHA = 0.15f
private const val GROUND_FILL_ALPHA = 0.35f
private val RIGHT_PAD = 16.dp

/**
 * Altitude over time (ADR 0016): the flight in the map's track colour, so the two read as one, and the recording
 * before and after it in the neutral colour. Hovering reads out a fix and shows it on the map through [onHighlight].
 * Its key numbers are in the track card beside it, which stands in for a table.
 */
@Composable
fun AltitudeProfile(
    track: AnalysedTrack,
    // Null while it is being read; drawn under the flight once it is there.
    ground: GroundProfile?,
    groundFailure: Failure?,
    highlighted: TrackFix?,
    onHighlight: (TrackFix?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fixes = remember(track) { track.fixes.filter { it.altitudeMeters != null } }
    if (fixes.size < 2) return
    val theme = if (LocalDarkTheme.current) ChartTheme.Dark else ChartTheme.Light
    val flightColor = MaterialTheme.extraColors.map.track
    val maxAltitude = fixes.maxOf { checkNotNull(it.altitudeMeters) }
    val shownGround = ground?.takeIf { it.points.size >= 2 }
    val top = maxOf(maxAltitude, shownGround?.points?.maxOf { it.elevationMeters } ?: 0)
    val description = stringResource(Res.string.profile_description, maxAltitude)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Legend(flightColor, theme, withGround = shownGround != null)
        ProfileChart(
            ProfileData(track, fixes, niceScale(top.toDouble()), shownGround),
            ProfileColors(flightColor, theme),
            highlighted,
            onHighlight,
            Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = description },
        )
        GroundNote(ground, groundFailure)
    }
}

/** Where the ground comes from, as its sources ask, and whether part of it is missing. */
@Composable
private fun GroundNote(
    ground: GroundProfile?,
    failure: Failure?,
) {
    val style = MaterialTheme.typography.labelSmall
    failure?.let { Text(it.message(), style = style, color = MaterialTheme.colorScheme.error) }
    if (ground == null) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (!ground.complete) Text(stringResource(Res.string.profile_ground_incomplete), style = style, color = color)
    if (ground.points.isNotEmpty()) Text(stringResource(Res.string.profile_ground_source), style = style, color = color)
}

private class ProfileData(
    val track: AnalysedTrack,
    // Only fixes with an altitude, which is all a profile can draw.
    val fixes: List<TrackFix>,
    val scale: AxisScale,
    val ground: GroundProfile?,
) {
    val start: Instant = fixes.first().time
    val end: Instant = fixes.last().time
}

private class ProfileColors(
    val flight: Color,
    val theme: ChartTheme,
)

@Composable
private fun Legend(
    flightColor: Color,
    theme: ChartTheme,
    withGround: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(flightColor, stringResource(Res.string.profile_legend_flight))
        LegendItem(theme.otherColor, stringResource(Res.string.profile_legend_outside_flight))
        if (withGround) LegendItem(theme.ground, stringResource(Res.string.profile_legend_terrain))
    }
}

@Composable
private fun LegendItem(
    color: Color,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(color)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileChart(
    data: ProfileData,
    colors: ProfileColors,
    highlighted: TrackFix?,
    onHighlight: (TrackFix?) -> Unit,
    modifier: Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    val plot = rememberPlotArea(size, RIGHT_PAD)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.theme.textSecondary)
    val valueLabels = data.scale.ticks.map { stringResource(Res.string.flight_altitude, it.toInt()) }
    val zone = TimeZone.currentSystemDefault()
    val ticks = remember(data) { timeTicks(data.start, data.end, MAX_TIME_TICKS) }
    val timeLabels = ticks.map { it.toLocalDateTime(zone).time.toDisplay() }
    val currentOnHighlight by rememberUpdatedState(onHighlight)
    LaunchedEffect(pointer, plot, data) {
        currentOnHighlight(pointer?.takeIf { it in plot }?.let { data.fixes.nearestTo(plot.timeAt(it.x, data)) })
    }

    Box(
        modifier =
            modifier
                .background(colors.theme.surface, MaterialTheme.shapes.medium)
                .onSizeChanged { size = it }
                // The handler is set up once, so it only records where the pointer is; the fix is found below.
                .trackPointer { pointer = it },
    ) {
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val segments = data.track.segments()
                val withAltitude = { list: List<TrackFix> -> list.filter { it.altitudeMeters != null } }
                val flown = withAltitude(segments.flown)
                val linePaths =
                    listOf(
                        withAltitude(segments.before) to colors.theme.otherColor,
                        withAltitude(segments.after) to colors.theme.otherColor,
                        flown to colors.flight,
                    ).filter { it.first.size >= 2 }.map { (part, color) -> plot.lineOf(part, data) to color }
                val fill = flown.takeIf { it.size >= 2 }?.let { plot.areaUnder(it, data) }
                val ground = data.ground?.let { plot.groundLineOf(it, data) }
                onDrawBehind {
                    drawValueAxis(plot, data.scale, valueLabels, measurer, labelStyle, colors.theme.grid)
                    // Under the flight, which is what the pilot reads first.
                    ground?.let { (line, area) ->
                        drawPath(area, colors.theme.ground.copy(alpha = GROUND_FILL_ALPHA))
                        drawPath(line, colors.theme.ground, style = Stroke(1.5.dp.toPx()))
                    }
                    ticks.forEachIndexed { index, tick ->
                        val label = measurer.measure(timeLabels[index], labelStyle)
                        val x = plot.xOf(tick, data)
                        drawText(label, topLeft = Offset(x - label.size.width / 2f, plot.bottom + 6.dp.toPx()))
                    }
                    fill?.let { drawPath(it, colors.flight.copy(alpha = FLIGHT_FILL_ALPHA)) }
                    linePaths.forEach { (path, color) -> drawPath(path, color, style = Stroke(2.dp.toPx())) }
                }
            },
        )
        val cursor = highlighted?.takeIf { it.altitudeMeters != null && it.time in data.start..data.end }
        if (cursor != null) {
            Canvas(Modifier.fillMaxSize()) {
                val x = plot.xOf(cursor.time, data)
                drawLine(colors.theme.textSecondary, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1f)
                val y = plot.yOf(checkNotNull(cursor.altitudeMeters).toDouble(), data.scale)
                drawRingedDot(Offset(x, y), colors.flight, colors.theme.surface)
            }
        }
        val position = pointer?.takeIf { it in plot }
        if (cursor != null && position != null) {
            val tooltipWidth = with(LocalDensity.current) { TOOLTIP_WIDTH.toPx() }
            val offset = tooltipOffset(position, size.width, tooltipWidth)
            val time = cursor.time.toLocalDateTime(zone).time
            val altitude = checkNotNull(cursor.altitudeMeters)
            val groundHere = data.ground?.elevationAt(cursor.time)?.roundToInt()
            ChartTooltip(
                title = "${time.toDisplay()}:${time.second.toString().padStart(2, '0')}",
                rows =
                    listOfNotNull(
                        TooltipRow(
                            colors.flight,
                            stringResource(Res.string.profile_altitude),
                            stringResource(Res.string.flight_altitude, altitude),
                        ),
                        groundHere?.let {
                            TooltipRow(
                                colors.theme.ground,
                                stringResource(Res.string.profile_legend_terrain),
                                stringResource(Res.string.flight_altitude, it),
                            )
                        },
                        groundHere?.let {
                            TooltipRow(
                                null,
                                stringResource(Res.string.profile_ground_above),
                                stringResource(Res.string.flight_altitude, altitude - it),
                            )
                        },
                    ),
                theme = colors.theme,
                modifier = Modifier.offset { offset },
            )
        }
    }
}

/** The ground's outline and the area under it, down to the axis. */
private fun PlotArea.groundLineOf(
    ground: GroundProfile,
    data: ProfileData,
): Pair<Path, Path> {
    val points = ground.points.map { Offset(xOf(it.time, data), yOf(it.elevationMeters.toDouble(), data.scale)) }
    val line =
        Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
    val area =
        Path().apply {
            addPath(line)
            lineTo(points.last().x, bottom)
            lineTo(points.first().x, bottom)
            close()
        }
    return line to area
}

private fun PlotArea.xOf(
    time: Instant,
    data: ProfileData,
): Float {
    val span = (data.end - data.start).inWholeMilliseconds.coerceAtLeast(1)
    return left + width * ((time - data.start).inWholeMilliseconds.toFloat() / span)
}

private fun PlotArea.timeAt(
    x: Float,
    data: ProfileData,
): Instant {
    val share = ((x - left) / width).coerceIn(0f, 1f)
    val span = (data.end - data.start).inWholeMilliseconds
    return data.start + (span * share).toLong().milliseconds
}

private fun PlotArea.lineOf(
    fixes: List<TrackFix>,
    data: ProfileData,
): Path =
    Path().apply {
        fixes.forEachIndexed { index, fix ->
            val x = xOf(fix.time, data)
            val y = yOf(checkNotNull(fix.altitudeMeters).toDouble(), data.scale)
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
    }

private fun PlotArea.areaUnder(
    fixes: List<TrackFix>,
    data: ProfileData,
): Path =
    lineOf(fixes, data).apply {
        lineTo(xOf(fixes.last().time, data), bottom)
        lineTo(xOf(fixes.first().time, data), bottom)
        close()
    }
