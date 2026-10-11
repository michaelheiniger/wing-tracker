package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.statistics.SeriesKey
import kotlin.math.roundToInt

/** A series as the charts draw it: identity, localized name and its fixed color. */
data class ChartSeries(
    val key: SeriesKey,
    val name: String,
    val color: Color,
)

/** Reports the pointer position inside the chart, or null once it leaves; drives crosshair and tooltip. */
fun Modifier.trackPointer(onMove: (Offset?) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                onMove(if (event.type == PointerEventType.Exit) null else event.changes.firstOrNull()?.position)
            }
        }
    }

/** A dot with a ring in the surface color, so it stays legible where marks overlap. */
fun DrawScope.drawRingedDot(
    center: Offset,
    color: Color,
    surface: Color,
) {
    drawCircle(surface, radius = 6.dp.toPx(), center = center)
    drawCircle(color, radius = 4.dp.toPx(), center = center)
}

fun formatTick(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

data class TooltipRow(
    val color: Color?,
    val label: String,
    val value: String,
)

val TOOLTIP_WIDTH = 220.dp

@Composable
fun ChartTooltip(
    title: String,
    rows: List<TooltipRow>,
    theme: ChartTheme,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(TOOLTIP_WIDTH),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                color = theme.textPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            )
            rows.forEach { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (row.color != null) {
                        Box(Modifier.size(10.dp).background(row.color, RoundedCornerShape(2.dp)))
                    }
                    Text(
                        row.label,
                        color = theme.textSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        row.value,
                        color = theme.textPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
fun LegendDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

/** The drawable area inside the axes; one place for the padding so both charts line up. */
class PlotArea(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val bottom: Float get() = top + height
    val right: Float get() = left + width

    operator fun contains(point: Offset): Boolean = point.x in left..right && point.y in top..bottom

    /** Maps a value on [scale] to a vertical position; zero sits on the baseline. */
    fun yOf(
        value: Double,
        scale: AxisScale,
    ): Float = bottom - (value / scale.max * height).toFloat()
}

@Composable
fun rememberPlotArea(
    size: IntSize,
    rightPad: Dp,
): PlotArea {
    val density = LocalDensity.current
    val left = with(density) { AXIS_LEFT_PAD.toPx() }
    val right = with(density) { rightPad.toPx() }
    val top = with(density) { AXIS_TOP_PAD.toPx() }
    val bottom = with(density) { AXIS_BOTTOM_PAD.toPx() }
    return PlotArea(
        left = left,
        top = top,
        width = (size.width - left - right).coerceAtLeast(1f),
        height = (size.height - top - bottom).coerceAtLeast(1f),
    )
}

/** Horizontal hairline gridlines with their value labels; recessive so the data stays loudest. */
fun DrawScope.drawValueAxis(
    plot: PlotArea,
    scale: AxisScale,
    labels: List<String>,
    measurer: TextMeasurer,
    style: TextStyle,
    gridColor: Color,
) {
    scale.ticks.forEachIndexed { index, tick ->
        val y = plot.yOf(tick, scale)
        drawLine(gridColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1f)
        val label = measurer.measure(labels[index], style)
        drawText(label, topLeft = Offset(plot.left - label.size.width - 8.dp.toPx(), y - label.size.height / 2f))
    }
}

/** Keeps the tooltip beside the pointer, flipping to its left near the right edge. */
fun tooltipOffset(
    pointer: Offset,
    chartWidth: Int,
    tooltipWidthPx: Float,
): IntOffset {
    val flip = pointer.x + tooltipWidthPx + TOOLTIP_MARGIN_PX > chartWidth
    val x = if (flip) pointer.x - tooltipWidthPx - TOOLTIP_GAP_PX else pointer.x + TOOLTIP_GAP_PX
    return IntOffset(x.toInt(), (pointer.y - TOOLTIP_GAP_PX / 2).toInt().coerceAtLeast(0))
}

private val AXIS_LEFT_PAD = 48.dp
private val AXIS_TOP_PAD = 16.dp
private val AXIS_BOTTOM_PAD = 28.dp
private const val TOOLTIP_MARGIN_PX = 32
private const val TOOLTIP_GAP_PX = 16
