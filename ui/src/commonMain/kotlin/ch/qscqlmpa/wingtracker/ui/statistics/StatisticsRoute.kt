package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import ch.qscqlmpa.wingtracker.statistics.GetStatistics

@Composable
fun StatisticsRoute(
    getStatistics: GetStatistics,
    onGoToLogbook: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val presenter = remember { StatisticsPresenter(scope, getStatistics) }
    LaunchedEffect(Unit) { presenter.refresh() }
    StatisticsScreen(presenter, onGoToLogbook)
}
