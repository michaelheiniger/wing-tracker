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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import ch.qscqlmpa.wingtracker.ui.resources.statistics_total
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.math.min

private const val MIN_LABELLED_BAND_DP = 40
private const val HOVER_WASH_ALPHA = 0.08f

private class ColumnStyle(
    val labels: TextStyle,
    val totals: TextStyle,
)

/** Stacked columns, one per year, one segment per visible wing. */
@Composable
fun YearlyChart(
    years: List<YearColumn>,
    series: List<ChartSeries>,
    unit: ChartUnit,
    theme: ChartTheme,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    var hover by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val plot = rememberPlotArea(size, rightPad = 8.dp)

    val scale = niceScale(years.maxOfOrNull { it.totalOf(series) } ?: 0.0, wholeNumbers = unit.wholeNumbers)
    val tickLabels = scale.ticks.map { unit.short(it) }
    val style =
        ColumnStyle(
            labels = MaterialTheme.typography.labelSmall.copy(color = theme.textSecondary),
            totals = MaterialTheme.typography.labelSmall.copy(color = theme.textPrimary),
        )
    val band = if (years.isEmpty()) plot.width else plot.width / years.size
    val hoveredIndex =
        hover
            ?.takeIf {
                it in plot
            }?.let { ((it.x - plot.left) / band).toInt().coerceIn(0, years.lastIndex) }

    Box(modifier.onSizeChanged { size = it }.trackPointer { hover = it }) {
        Canvas(Modifier.fillMaxSize()) {
            drawValueAxis(plot, scale, tickLabels, measurer, style.labels, theme.grid)
            hoveredIndex?.let {
                drawRect(
                    theme.textSecondary.copy(alpha = HOVER_WASH_ALPHA),
                    Offset(plot.left + band * it, plot.top),
                    Size(band, plot.height),
                )
            }
            val labelEvery = ceil(MIN_LABELLED_BAND_DP.dp.toPx() / band).toInt().coerceAtLeast(1)
            years.forEachIndexed { index, year ->
                val centerX = plot.left + band * (index + 0.5f)
                val capHeight = drawColumn(year, series, scale, plot, centerX, band)
                if (index % labelEvery == 0) drawYearLabel(year.year.toString(), centerX, plot, measurer, style.labels)
                // Totals sit on the cap, only where a column is wide enough to carry them without clutter.
                if (band >= MIN_LABELLED_BAND_DP.dp.toPx() && capHeight > 0f) {
                    drawTotal(
                        formatTick(year.totalOf(series)),
                        centerX,
                        plot.bottom - capHeight,
                        measurer,
                        style.totals,
                    )
                }
            }
        }
        val pointer = hover
        if (hoveredIndex != null && pointer != null) {
            YearTooltip(years[hoveredIndex], series, unit, theme, pointer, size)
        }
    }
}

/** Draws one stacked column and returns its height, so the caller can place the total on top. */
private fun DrawScope.drawColumn(
    year: YearColumn,
    series: List<ChartSeries>,
    scale: AxisScale,
    plot: PlotArea,
    centerX: Float,
    band: Float,
): Float {
    val barWidth = min(24.dp.toPx(), band * 0.6f)
    val gap = 2.dp.toPx()
    val radius = 4.dp.toPx()
    val present = series.filter { year.of(it.key) > 0.0 }
    var stacked = 0f
    present.forEachIndexed { position, s ->
        val height = (year.of(s.key) / scale.max * plot.height).toFloat()
        val segmentBottom = plot.bottom - stacked
        stacked += height
        // A 2px gap in the surface color separates stacked segments; no stroke is drawn around marks.
        val drawBottom = if (position == 0) segmentBottom else segmentBottom - gap
        val rect = Rect(centerX - barWidth / 2, segmentBottom - height, centerX + barWidth / 2, drawBottom)
        if (rect.height > 0f) {
            // Only the data end (the top of the column) is rounded; the baseline stays square.
            val corner =
                if (position ==
                    present.lastIndex
                ) {
                    CornerRadius(min(radius, rect.height))
                } else {
                    CornerRadius.Zero
                }
            val path =
                Path().apply {
                    addRoundRect(
                        RoundRect(rect, corner, corner, CornerRadius.Zero, CornerRadius.Zero),
                    )
                }
            drawPath(path, s.color)
        }
    }
    return stacked
}

private fun DrawScope.drawYearLabel(
    text: String,
    centerX: Float,
    plot: PlotArea,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val label = measurer.measure(text, style)
    drawText(label, topLeft = Offset(centerX - label.size.width / 2f, plot.bottom + 8.dp.toPx()))
}

private fun DrawScope.drawTotal(
    text: String,
    centerX: Float,
    capY: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val label = measurer.measure(text, style)
    drawText(label, topLeft = Offset(centerX - label.size.width / 2f, capY - label.size.height - 2.dp.toPx()))
}

@Composable
private fun YearTooltip(
    year: YearColumn,
    series: List<ChartSeries>,
    unit: ChartUnit,
    theme: ChartTheme,
    pointer: Offset,
    size: IntSize,
) {
    val tooltipWidth = with(LocalDensity.current) { TOOLTIP_WIDTH.toPx() }
    val rows =
        series
            .filter { year.of(it.key) > 0.0 }
            .map { TooltipRow(it.color, it.name, unit.full(year.of(it.key))) }
    ChartTooltip(
        title = year.year.toString(),
        rows = rows + TooltipRow(null, stringResource(Res.string.statistics_total), unit.full(year.totalOf(series))),
        theme = theme,
        modifier = Modifier.offset { tooltipOffset(pointer, size.width, tooltipWidth) },
    )
}
