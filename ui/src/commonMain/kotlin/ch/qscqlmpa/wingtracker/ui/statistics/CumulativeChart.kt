package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.statistics.SeriesKey
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.month_names
import ch.qscqlmpa.wingtracker.ui.toDisplay
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringArrayResource
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val DAYS_FOR_MONTH_TICKS = 400
private const val MIN_TICK_SPACING_DP = 64
private const val MAX_DIRECT_LABELS = 4
private const val MIN_LABEL_GAP_DP = 14
private const val CROSSHAIR_ALPHA = 0.5f
private const val MONTH_LABEL_LENGTH = 3
private const val MONTHS_PER_YEAR = 12

private fun LocalDate.day(): Int = toEpochDays().toInt()

/** Maps dates and values onto the plot; shared by the lines, the end labels and the hover layer. */
private class TimePlot(
    val plot: PlotArea,
    val firstDay: Int,
    val lastDay: Int,
    val scale: AxisScale,
) {
    private val span: Float get() = (lastDay - firstDay).toFloat()

    fun xOf(day: Int): Float = plot.left + (day - firstDay) / span * plot.width

    fun yOf(value: Double): Float = plot.yOf(value, scale)

    fun dayAt(x: Float): Int = (firstDay + (x - plot.left) / plot.width * span).roundToInt().coerceIn(firstDay, lastDay)
}

/** One step line per visible wing: totals stay flat between flights, so steps are the honest shape. */
@Composable
fun CumulativeChart(
    cumulative: Map<SeriesKey, List<ValuePoint>>,
    until: LocalDate,
    series: List<ChartSeries>,
    unit: ChartUnit,
    theme: ChartTheme,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    var hover by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val monthNames = stringArrayResource(Res.array.month_names)
    val minTickSpacingPx = with(LocalDensity.current) { MIN_TICK_SPACING_DP.dp.toPx() }

    val lines = series.associateWith { cumulative[it.key].orEmpty() }.filterValues { it.isNotEmpty() }
    val firstDay = lines.values.minOfOrNull { it.first().date.day() } ?: until.day()
    val scale =
        niceScale(lines.values.maxOfOrNull { line -> line.maxOf { it.value } } ?: 0.0, wholeNumbers = unit.wholeNumbers)
    val time =
        TimePlot(rememberPlotArea(size, rightPad = 64.dp), firstDay, maxOf(firstDay + 1, until.day()), scale)

    val tickLabels = scale.ticks.map { unit.short(it) }
    val endLabels = lines.mapValues { (_, points) -> unit.short(points.last().value) }
    val xTicks =
        remember(
            time.firstDay,
            time.lastDay,
            monthNames,
            time.plot.width,
        ) { timeTicks(time, monthNames, minTickSpacingPx) }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = theme.textSecondary)
    val endStyle = MaterialTheme.typography.labelSmall.copy(color = theme.textPrimary)
    val hoverDay = hover?.takeIf { it in time.plot }?.let { time.dayAt(it.x) }

    Box(modifier.onSizeChanged { size = it }.trackPointer { hover = it }) {
        Canvas(Modifier.fillMaxSize()) {
            drawValueAxis(time.plot, scale, tickLabels, measurer, labelStyle, theme.grid)
            xTicks.forEach { (day, text) -> drawTimeLabel(text, time.xOf(day), time.plot, measurer, labelStyle) }
            lines.forEach { (s, points) -> drawStepLine(points, s.color, time) }
            drawEndMarks(lines, endLabels, time, theme, measurer, endStyle)
            hoverDay?.let { drawHoverLayer(it, lines, time, theme) }
        }
        val pointer = hover
        if (hoverDay != null && pointer != null) {
            DayTooltip(hoverDay, lines, unit, theme, pointer, size)
        }
    }
}

private fun DrawScope.drawStepLine(
    points: List<ValuePoint>,
    color: androidx.compose.ui.graphics.Color,
    time: TimePlot,
) {
    val path = Path()
    var previousY = 0f
    points.forEachIndexed { index, point ->
        val x = time.xOf(point.date.day())
        val y = time.yOf(point.value)
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            // Totals are flat between flights: go across first, then up.
            path.lineTo(x, previousY)
            path.lineTo(x, y)
        }
        previousY = y
    }
    drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** End dots carry each wing's current total; direct labels stay few and are never stacked on each other. */
private fun DrawScope.drawEndMarks(
    lines: Map<ChartSeries, List<ValuePoint>>,
    labels: Map<ChartSeries, String>,
    time: TimePlot,
    theme: ChartTheme,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    var lastLabelY = Float.NEGATIVE_INFINITY
    lines.entries.sortedBy { time.yOf(it.value.last().value) }.forEach { (s, points) ->
        val end = points.last()
        val center = Offset(time.xOf(end.date.day()), time.yOf(end.value))
        drawRingedDot(center, s.color, theme.surface)
        val fits = abs(center.y - lastLabelY) >= MIN_LABEL_GAP_DP.dp.toPx()
        if (lines.size <= MAX_DIRECT_LABELS && fits) {
            val text = measurer.measure(labels.getValue(s), style)
            drawText(text, topLeft = Offset(center.x + 10.dp.toPx(), center.y - text.size.height / 2f))
            lastLabelY = center.y
        }
    }
}

private fun DrawScope.drawHoverLayer(
    day: Int,
    lines: Map<ChartSeries, List<ValuePoint>>,
    time: TimePlot,
    theme: ChartTheme,
) {
    val x = time.xOf(day)
    drawLine(
        theme.textSecondary.copy(alpha = CROSSHAIR_ALPHA),
        Offset(x, time.plot.top),
        Offset(x, time.plot.bottom),
        strokeWidth = 1f,
    )
    val date = LocalDate.fromEpochDays(day)
    lines.forEach { (s, points) ->
        if (points.first().date <= date) {
            drawRingedDot(Offset(x, time.yOf(points.valueAt(date))), s.color, theme.surface)
        }
    }
}

private fun DrawScope.drawTimeLabel(
    text: String,
    x: Float,
    plot: PlotArea,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val label = measurer.measure(text, style)
    drawText(label, topLeft = Offset(x - label.size.width / 2f, plot.bottom + 8.dp.toPx()))
}

@Composable
private fun DayTooltip(
    day: Int,
    lines: Map<ChartSeries, List<ValuePoint>>,
    unit: ChartUnit,
    theme: ChartTheme,
    pointer: Offset,
    size: IntSize,
) {
    val date = LocalDate.fromEpochDays(day)
    val tooltipWidth = with(LocalDensity.current) { TOOLTIP_WIDTH.toPx() }
    val rows =
        lines
            .filter { (_, points) -> points.first().date <= date }
            .map { (s, points) -> s to points.valueAt(date) }
            .sortedByDescending { it.second }
            .map { (s, total) -> TooltipRow(s.color, s.name, unit.full(total)) }
    ChartTooltip(
        title = date.toDisplay(),
        rows = rows,
        theme = theme,
        modifier = Modifier.offset { tooltipOffset(pointer, size.width, tooltipWidth) },
    )
}

/** Month starts for short ranges, year starts otherwise, thinned so labels never touch. */
private fun timeTicks(
    time: TimePlot,
    monthNames: List<String>,
    minSpacingPx: Float,
): List<Pair<Int, String>> {
    val first = LocalDate.fromEpochDays(time.firstDay)
    val last = LocalDate.fromEpochDays(time.lastDay)
    val byMonth = time.lastDay - time.firstDay <= DAYS_FOR_MONTH_TICKS
    val candidates = mutableListOf<Pair<Int, String>>()
    var year = first.year
    var month = if (byMonth) first.month.ordinal + 1 else 1
    var date = LocalDate(year, month, 1)
    while (date <= last) {
        if (date >= first) {
            val text = if (byMonth) "${monthNames[month - 1].take(MONTH_LABEL_LENGTH)} $year" else year.toString()
            candidates += date.day() to text
        }
        if (byMonth && month < MONTHS_PER_YEAR) {
            month++
        } else {
            month = 1
            year++
        }
        date = LocalDate(year, month, 1)
    }
    if (candidates.size < 2) return candidates
    val pxPerDay = time.plot.width / (time.lastDay - time.firstDay).coerceAtLeast(1)
    val spacingPx = pxPerDay * (candidates[1].first - candidates[0].first).coerceAtLeast(1)
    val stride = ceil(minSpacingPx / spacingPx).toInt().coerceAtLeast(1)
    return candidates.filterIndexed { index, _ -> index % stride == 0 }
}
