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
import ch.qscqlmpa.wingtracker.flights.igc.TrackFix
import ch.qscqlmpa.wingtracker.ui.flights.detail.map.segments
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.flight_altitude
import ch.qscqlmpa.wingtracker.ui.resources.profile_altitude
import ch.qscqlmpa.wingtracker.ui.resources.profile_description
import ch.qscqlmpa.wingtracker.ui.resources.profile_legend_flight
import ch.qscqlmpa.wingtracker.ui.resources.profile_legend_ground
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private const val MAX_TIME_TICKS = 6
private const val FLIGHT_FILL_ALPHA = 0.15f
private val RIGHT_PAD = 16.dp

/**
 * Altitude over time (ADR 0016): the flight in the map's track colour, so the two read as one, and the recording
 * before and after it in the neutral colour. Hovering reads out a fix and shows it on the map through [onHighlight].
 * Its key numbers are in the track card beside it, which stands in for a table.
 */
@Composable
fun AltitudeProfile(
    track: AnalysedTrack,
    highlighted: TrackFix?,
    onHighlight: (TrackFix?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fixes = remember(track) { track.fixes.filter { it.altitudeMeters != null } }
    if (fixes.size < 2) return
    val theme = if (LocalDarkTheme.current) ChartTheme.Dark else ChartTheme.Light
    val flightColor = MaterialTheme.extraColors.map.track
    val maxAltitude = fixes.maxOf { checkNotNull(it.altitudeMeters) }
    val description = stringResource(Res.string.profile_description, maxAltitude)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Legend(flightColor, theme)
        ProfileChart(
            ProfileData(track, fixes, niceScale(maxAltitude.toDouble())),
            ProfileColors(flightColor, theme),
            highlighted,
            onHighlight,
            Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = description },
        )
    }
}

private class ProfileData(
    val track: AnalysedTrack,
    // Only fixes with an altitude, which is all a profile can draw.
    val fixes: List<TrackFix>,
    val scale: AxisScale,
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
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(flightColor, stringResource(Res.string.profile_legend_flight))
        LegendItem(theme.otherColor, stringResource(Res.string.profile_legend_ground))
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
                onDrawBehind {
                    drawValueAxis(plot, data.scale, valueLabels, measurer, labelStyle, colors.theme.grid)
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
            ChartTooltip(
                title = "${time.toDisplay()}:${time.second.toString().padStart(2, '0')}",
                rows =
                    listOf(
                        TooltipRow(
                            colors.flight,
                            stringResource(Res.string.profile_altitude),
                            stringResource(Res.string.flight_altitude, checkNotNull(cursor.altitudeMeters)),
                        ),
                    ),
                theme = colors.theme,
                modifier = Modifier.offset { offset },
            )
        }
    }
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
