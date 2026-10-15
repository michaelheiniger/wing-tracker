package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.runtime.Composable
import ch.qscqlmpa.wingtracker.statistics.SeriesKey
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.chart_axis_hours
import ch.qscqlmpa.wingtracker.ui.resources.flights_count
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes

private const val MINUTES_PER_HOUR = 60.0

/** What a chart measures; the charts draw plain numbers and leave units and wording to this. */
enum class ChartUnit(
    // Counts have no fractions, so their axis never shows half a flight.
    val wholeNumbers: Boolean,
) {
    Hours(wholeNumbers = false),
    Flights(wholeNumbers = true),
}

/** Short form for axis ticks and line ends. */
@Composable
fun ChartUnit.short(value: Double): String =
    when (this) {
        ChartUnit.Hours -> stringResource(Res.string.chart_axis_hours, formatTick(value))
        ChartUnit.Flights -> formatTick(value)
    }

/** Full form for tooltips, where there is room to be exact. */
@Composable
fun ChartUnit.full(value: Double): String =
    when (this) {
        ChartUnit.Hours -> {
            formatDuration((value * MINUTES_PER_HOUR).roundToLong().minutes)
        }

        ChartUnit.Flights -> {
            val count = value.roundToInt()
            pluralStringResource(Res.plurals.flights_count, count, count)
        }
    }

/** One column of the yearly chart: a value per series. */
data class YearColumn(
    val year: Int,
    val values: Map<SeriesKey, Double>,
) {
    fun of(key: SeriesKey): Double = values[key] ?: 0.0

    fun totalOf(series: List<ChartSeries>): Double = series.sumOf { of(it.key) }
}

data class ValuePoint(
    val date: LocalDate,
    val value: Double,
)

/** Running totals are flat between flights, so the value on a date is the last point at or before it. */
fun List<ValuePoint>.valueAt(date: LocalDate): Double = lastOrNull { it.date <= date }?.value ?: 0.0
