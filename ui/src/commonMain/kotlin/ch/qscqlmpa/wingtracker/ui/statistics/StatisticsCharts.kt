package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.statistics.Statistics
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.chart_unit_flights
import ch.qscqlmpa.wingtracker.ui.resources.chart_unit_hours
import ch.qscqlmpa.wingtracker.ui.resources.statistics_cumulative_flights_note
import ch.qscqlmpa.wingtracker.ui.resources.statistics_cumulative_flights_title
import ch.qscqlmpa.wingtracker.ui.resources.statistics_cumulative_note
import ch.qscqlmpa.wingtracker.ui.resources.statistics_cumulative_title
import ch.qscqlmpa.wingtracker.ui.resources.statistics_yearly_flights_title
import ch.qscqlmpa.wingtracker.ui.resources.statistics_yearly_note
import ch.qscqlmpa.wingtracker.ui.resources.statistics_yearly_title
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

private val CHART_HEIGHT = 280.dp

@Composable
fun StatisticsCharts(
    state: StatisticsState,
    statistics: Statistics,
    series: List<ChartSeries>,
    theme: ChartTheme,
    onSelectUnit: (ChartCardKind, ChartUnit) -> Unit,
) {
    val chartModifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)
    val yearlyUnit = state.unitOf(ChartCardKind.Yearly)
    ChartCard(
        title =
            stringResource(
                when (yearlyUnit) {
                    ChartUnit.Hours -> Res.string.statistics_yearly_title
                    ChartUnit.Flights -> Res.string.statistics_yearly_flights_title
                },
            ),
        note = stringResource(Res.string.statistics_yearly_note),
        theme = theme,
        action = { UnitToggle(yearlyUnit) { onSelectUnit(ChartCardKind.Yearly, it) } },
    ) {
        val columns =
            when (yearlyUnit) {
                ChartUnit.Hours -> statistics.yearlyHourColumns()
                ChartUnit.Flights -> statistics.yearlyFlightColumns()
            }
        YearlyChart(columns, series, yearlyUnit, theme, chartModifier)
    }
    val cumulativeUnit = state.unitOf(ChartCardKind.Cumulative)
    ChartCard(
        title =
            stringResource(
                when (cumulativeUnit) {
                    ChartUnit.Hours -> Res.string.statistics_cumulative_title
                    ChartUnit.Flights -> Res.string.statistics_cumulative_flights_title
                },
            ),
        note =
            stringResource(
                when (cumulativeUnit) {
                    ChartUnit.Hours -> Res.string.statistics_cumulative_note
                    ChartUnit.Flights -> Res.string.statistics_cumulative_flights_note
                },
            ),
        theme = theme,
        action = { UnitToggle(cumulativeUnit) { onSelectUnit(ChartCardKind.Cumulative, it) } },
    ) {
        val lines =
            when (cumulativeUnit) {
                ChartUnit.Hours -> statistics.cumulativeHourLines()
                ChartUnit.Flights -> statistics.cumulativeFlightLines()
            }
        CumulativeChart(lines, statistics.today, series, cumulativeUnit, theme, chartModifier)
    }
}

/** A segmented control rather than a switch: both choices stay visible, and the selected one is marked by a check. */
@Composable
private fun UnitToggle(
    selected: ChartUnit,
    onSelect: (ChartUnit) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        ChartUnit.entries.forEachIndexed { index, unit ->
            SegmentedButton(
                selected = unit == selected,
                onClick = { onSelect(unit) },
                shape = SegmentedButtonDefaults.itemShape(index, ChartUnit.entries.size),
            ) {
                Text(
                    stringResource(
                        when (unit) {
                            ChartUnit.Hours -> Res.string.chart_unit_hours
                            ChartUnit.Flights -> Res.string.chart_unit_flights
                        },
                    ),
                )
            }
        }
    }
}

private const val MINUTES_PER_HOUR = 60.0

private fun Duration.hours(): Double = inWholeMinutes / MINUTES_PER_HOUR

private fun Statistics.yearlyHourColumns() =
    yearly.map { year -> YearColumn(year.year, year.hours.mapValues { (_, d) -> d.hours() }) }

private fun Statistics.yearlyFlightColumns() =
    yearlyFlights.map { year -> YearColumn(year.year, year.counts.mapValues { (_, n) -> n.toDouble() }) }

private fun Statistics.cumulativeHourLines() =
    cumulative.mapValues { (_, points) -> points.map { ValuePoint(it.date, it.total.hours()) } }

private fun Statistics.cumulativeFlightLines() =
    cumulativeFlights.mapValues { (_, points) -> points.map { ValuePoint(it.date, it.total.toDouble()) } }

@Composable
fun ChartCard(
    title: String,
    note: String,
    theme: ChartTheme,
    action: (@Composable () -> Unit)? = null,
    chart: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = theme.textPrimary)
                    Text(note, style = MaterialTheme.typography.bodySmall, color = theme.textSecondary)
                }
                action?.invoke()
            }
            Box { chart() }
        }
    }
}
