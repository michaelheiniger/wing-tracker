package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.statistics.Statistics
import ch.qscqlmpa.wingtracker.ui.resources.Res
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
    statistics: Statistics,
    series: List<ChartSeries>,
    theme: ChartTheme,
) {
    val chartModifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)
    ChartCard(
        stringResource(Res.string.statistics_yearly_title),
        stringResource(Res.string.statistics_yearly_note),
        theme,
    ) {
        YearlyChart(statistics.yearlyHourColumns(), series, ChartUnit.Hours, theme, chartModifier)
    }
    ChartCard(
        stringResource(Res.string.statistics_yearly_flights_title),
        stringResource(Res.string.statistics_yearly_note),
        theme,
    ) {
        YearlyChart(statistics.yearlyFlightColumns(), series, ChartUnit.Flights, theme, chartModifier)
    }
    ChartCard(
        stringResource(Res.string.statistics_cumulative_title),
        stringResource(Res.string.statistics_cumulative_note),
        theme,
    ) {
        CumulativeChart(
            statistics.cumulativeHourLines(),
            statistics.today,
            series,
            ChartUnit.Hours,
            theme,
            chartModifier,
        )
    }
    ChartCard(
        stringResource(Res.string.statistics_cumulative_flights_title),
        stringResource(Res.string.statistics_cumulative_flights_note),
        theme,
    ) {
        CumulativeChart(
            statistics.cumulativeFlightLines(),
            statistics.today,
            series,
            ChartUnit.Flights,
            theme,
            chartModifier,
        )
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
    chart: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = theme.textPrimary)
            Text(note, style = MaterialTheme.typography.bodySmall, color = theme.textSecondary)
            Box { chart() }
        }
    }
}
