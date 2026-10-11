package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.statistics.Series
import ch.qscqlmpa.wingtracker.statistics.SeriesKey
import ch.qscqlmpa.wingtracker.statistics.Statistics
import ch.qscqlmpa.wingtracker.ui.components.UnreadableBanner
import ch.qscqlmpa.wingtracker.ui.formatHoursAndMinutes
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_dismiss
import ch.qscqlmpa.wingtracker.ui.resources.flight_wing_retired
import ch.qscqlmpa.wingtracker.ui.resources.series_other
import ch.qscqlmpa.wingtracker.ui.resources.statistics_all_hidden
import ch.qscqlmpa.wingtracker.ui.resources.statistics_cumulative_note
import ch.qscqlmpa.wingtracker.ui.resources.statistics_cumulative_title
import ch.qscqlmpa.wingtracker.ui.resources.statistics_empty_action
import ch.qscqlmpa.wingtracker.ui.resources.statistics_empty_body
import ch.qscqlmpa.wingtracker.ui.resources.statistics_empty_title
import ch.qscqlmpa.wingtracker.ui.resources.statistics_legend_hint
import ch.qscqlmpa.wingtracker.ui.resources.statistics_show_charts
import ch.qscqlmpa.wingtracker.ui.resources.statistics_show_table
import ch.qscqlmpa.wingtracker.ui.resources.statistics_table_cumulative_title
import ch.qscqlmpa.wingtracker.ui.resources.statistics_table_year
import ch.qscqlmpa.wingtracker.ui.resources.statistics_table_yearly_title
import ch.qscqlmpa.wingtracker.ui.resources.statistics_title
import ch.qscqlmpa.wingtracker.ui.resources.statistics_total
import ch.qscqlmpa.wingtracker.ui.resources.statistics_yearly_note
import ch.qscqlmpa.wingtracker.ui.resources.statistics_yearly_title
import ch.qscqlmpa.wingtracker.ui.resources.unreadable_kept
import ch.qscqlmpa.wingtracker.ui.resources.unreadable_quarantined
import ch.qscqlmpa.wingtracker.ui.theme.LocalDarkTheme
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

private val CHART_HEIGHT = 280.dp

@Composable
fun StatisticsScreen(
    presenter: StatisticsPresenter,
    onGoToLogbook: () -> Unit,
) {
    val state = presenter.state
    val statistics = state.statistics

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.statistics_title), style = MaterialTheme.typography.headlineMedium)
            if (statistics != null && !statistics.isEmpty) {
                OutlinedButton(onClick = presenter::toggleTable) {
                    Text(
                        stringResource(
                            if (state.showTable) {
                                Res.string.statistics_show_charts
                            } else {
                                Res.string.statistics_show_table
                            },
                        ),
                    )
                }
            }
        }
        state.unreadable.forEach { UnreadableBanner(it, onDismiss = presenter::dismissUnreadable) }
        state.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }

        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        } else if (statistics != null) {
            if (statistics.isEmpty) {
                EmptyStatistics(onGoToLogbook)
            } else {
                StatisticsContent(state, statistics, presenter)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatisticsContent(
    state: StatisticsState,
    statistics: Statistics,
    presenter: StatisticsPresenter,
) {
    val theme = if (LocalDarkTheme.current) ChartTheme.Dark else ChartTheme.Light

    fun colorOf(key: SeriesKey) = if (key == SeriesKey.Other) theme.otherColor else theme.colorOf(state.slotOf(key))

    val names = statistics.series.associate { it.key to it.displayName() }
    val chartSeries = state.visibleSeries.map { ChartSeries(it.key, names.getValue(it.key), colorOf(it.key)) }

    // A legend is always shown: it is the dependable identity channel and the control for hiding wings.
    Text(
        stringResource(Res.string.statistics_legend_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        statistics.series.forEach { s ->
            FilterChip(
                selected = s.key !in state.hidden,
                onClick = { presenter.toggleSeries(s.key) },
                label = { Text(names.getValue(s.key)) },
                leadingIcon = { LegendDot(colorOf(s.key)) },
            )
        }
    }

    when {
        chartSeries.isEmpty() -> {
            Text(stringResource(Res.string.statistics_all_hidden), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        state.showTable -> {
            StatisticsTables(statistics, chartSeries)
        }

        else -> {
            ChartCard(
                stringResource(Res.string.statistics_yearly_title),
                stringResource(Res.string.statistics_yearly_note),
                theme,
            ) {
                YearlyHoursChart(statistics.yearly, chartSeries, theme, Modifier.fillMaxWidth().height(CHART_HEIGHT))
            }
            ChartCard(
                stringResource(Res.string.statistics_cumulative_title),
                stringResource(Res.string.statistics_cumulative_note),
                theme,
            ) {
                CumulativeHoursChart(statistics, chartSeries, theme, Modifier.fillMaxWidth().height(CHART_HEIGHT))
            }
        }
    }
}

@Composable
private fun ChartCard(
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

/** The table view: every number the charts draw, readable without color or a pointer. */
@Composable
private fun StatisticsTables(
    statistics: Statistics,
    series: List<ChartSeries>,
) {
    val firstColumn = 72.dp
    val column = 120.dp

    @Composable
    fun Cell(
        text: String,
        width: androidx.compose.ui.unit.Dp,
        bold: Boolean = false,
    ) = Text(
        text,
        modifier = Modifier.width(width),
        style = if (bold) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
    )

    @Composable
    fun Table(
        title: String,
        rows: List<Pair<String, List<Duration>>>,
        withTotal: Boolean,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Column(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row {
                Cell(stringResource(Res.string.statistics_table_year), firstColumn, bold = true)
                series.forEach { Cell(it.name, column, bold = true) }
                if (withTotal) Cell(stringResource(Res.string.statistics_total), column, bold = true)
            }
            rows.forEach { (label, values) ->
                Row {
                    Cell(label, firstColumn)
                    values.forEach { Cell(formatHoursAndMinutes(it.inWholeMinutes), column) }
                    if (withTotal) {
                        val total = values.fold(Duration.ZERO) { a, b -> a + b }
                        Cell(formatHoursAndMinutes(total.inWholeMinutes), column)
                    }
                }
            }
        }
    }

    Table(
        title = stringResource(Res.string.statistics_table_yearly_title),
        rows =
            statistics.yearly.map { year ->
                year.year.toString() to series.map { year.hours[it.key] ?: Duration.ZERO }
            },
        withTotal = true,
    )
    Table(
        title = stringResource(Res.string.statistics_table_cumulative_title),
        rows =
            statistics.yearly.map { year ->
                // The current year ends today, because the line does not extend into the future.
                val yearEnd = minOf(LocalDate(year.year, 12, 31), statistics.today)
                year.year.toString() to series.map { statistics.cumulativeAt(it.key, yearEnd) }
            },
        withTotal = false,
    )
}

@Composable
private fun EmptyStatistics(onGoToLogbook: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.statistics_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(Res.string.statistics_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onGoToLogbook) { Text(stringResource(Res.string.statistics_empty_action)) }
    }
}

@Composable
private fun Series.displayName(): String =
    when (key) {
        is SeriesKey.OfWing -> {
            val wing = wings.single()
            if (wing.isActive) wing.details.name else stringResource(Res.string.flight_wing_retired, wing.details.name)
        }

        SeriesKey.Other -> {
            stringResource(Res.string.series_other, wings.size)
        }
    }
