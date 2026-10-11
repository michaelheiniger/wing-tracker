package ch.qscqlmpa.wingtracker.ui.statistics

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** A value axis that starts at zero and ends on a round number, so ticks read as 0 / 10 / 20 rather than 0 / 13.7. */
data class AxisScale(
    val max: Double,
    val step: Double,
) {
    val ticks: List<Double> get() = generateSequence(0.0) { it + step }.takeWhile { it <= max + step / 2 }.toList()
}

private val STEP_MULTIPLIERS = listOf(1.0, 2.0, 5.0, 10.0)

fun niceScale(
    maxValue: Double,
    targetTicks: Int = 5,
): AxisScale {
    val safeMax = if (maxValue <= 0.0) 1.0 else maxValue
    val magnitude = 10.0.pow(floor(log10(safeMax / targetTicks)))
    val step = STEP_MULTIPLIERS.map { it * magnitude }.first { safeMax / it <= targetTicks }
    return AxisScale(max = ceil(safeMax / step) * step, step = step)
}
