package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import ch.qscqlmpa.wingtracker.flights.FlightFilter
import ch.qscqlmpa.wingtracker.statistics.GetStatistics

@Composable
fun StatisticsRoute(
    getStatistics: GetStatistics,
    filter: FlightFilter?,
    onShowAll: () -> Unit,
    onGoToLogbook: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val presenter = remember(filter) { StatisticsPresenter(scope, getStatistics, filter) }
    LaunchedEffect(presenter) { presenter.refresh() }
    StatisticsScreen(presenter, onShowAll, onGoToLogbook)
}
