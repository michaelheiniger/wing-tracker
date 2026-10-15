package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.statistics.Series
import ch.qscqlmpa.wingtracker.statistics.SeriesKey
import ch.qscqlmpa.wingtracker.statistics.Statistics
import ch.qscqlmpa.wingtracker.ui.components.UnreadableBanner
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.label
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.flight_wing_retired
import ch.qscqlmpa.wingtracker.ui.resources.series_other
import ch.qscqlmpa.wingtracker.ui.resources.statistics_all_hidden
import ch.qscqlmpa.wingtracker.ui.resources.statistics_empty_action
import ch.qscqlmpa.wingtracker.ui.resources.statistics_empty_body
import ch.qscqlmpa.wingtracker.ui.resources.statistics_empty_title
import ch.qscqlmpa.wingtracker.ui.resources.statistics_legend_hint
import ch.qscqlmpa.wingtracker.ui.resources.statistics_show_charts
import ch.qscqlmpa.wingtracker.ui.resources.statistics_show_table
import ch.qscqlmpa.wingtracker.ui.resources.statistics_title
import ch.qscqlmpa.wingtracker.ui.theme.LocalDarkTheme
import org.jetbrains.compose.resources.stringResource

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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The tables show both units, so the switch only makes sense for the charts.
                    if (!state.showTable) UnitToggle(state.unit, presenter::selectUnit)
                    StatisticsViewButton(state.showTable, presenter::toggleTable)
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
            StatisticsCharts(state.unit, statistics, chartSeries, theme)
        }
    }

    // Not tied to the legend: it is about checks of each active wing, not about the charted series.
    SinceLastCheckCard(statistics.sinceLastCheck, theme)
}

@Composable
private fun StatisticsViewButton(
    showTable: Boolean,
    onToggle: () -> Unit,
) {
    OutlinedButton(onClick = onToggle) {
        Text(
            stringResource(
                if (showTable) Res.string.statistics_show_charts else Res.string.statistics_show_table,
            ),
        )
    }
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
