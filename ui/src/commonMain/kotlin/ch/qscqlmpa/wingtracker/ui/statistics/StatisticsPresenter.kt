package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.FlightFilter
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.statistics.Series
import ch.qscqlmpa.wingtracker.statistics.SeriesKey
import ch.qscqlmpa.wingtracker.statistics.Statistics
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class StatisticsState(
    val statistics: Statistics? = null,
    // Which wings are hidden is remembered only while the screen lives; it is a view choice, not data.
    val hidden: Set<SeriesKey> = emptySet(),
    val showTable: Boolean = false,
    // One unit for all charts, so the yearly and cumulative views always tell the same story.
    val unit: ChartUnit = ChartUnit.Flights,
    val unreadable: List<UnreadableRecord> = emptyList(),
    val failure: Failure? = null,
) {
    val loading: Boolean get() = statistics == null && failure == null

    val visibleSeries: List<Series> get() = statistics?.shownSeries.orEmpty().filter { it.key !in hidden }

    /** The slot is the position among all series, not among visible ones, so colors never shift. */
    fun slotOf(key: SeriesKey): Int = statistics?.series?.indexOfFirst { it.key == key } ?: 0
}

class StatisticsPresenter(
    private val scope: CoroutineScope,
    private val getStatistics: GetStatistics,
    // Set when the statistics were opened for a logbook search.
    val filter: FlightFilter? = null,
) {
    var state by mutableStateOf(StatisticsState())
        private set

    fun refresh() {
        scope.launch {
            state =
                try {
                    val statistics = getStatistics(filter ?: FlightFilter())
                    state.copy(
                        statistics = statistics,
                        unreadable = (state.unreadable + statistics.unreadable).distinct(),
                        failure = null,
                    )
                } catch (e: CancellationException) {
                    throw e
                    // Any storage failure must reach the user as a message; it must never crash the window.
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    state.copy(failure = Failure(FailureKind.LoadStatistics, e.message))
                }
        }
    }

    fun toggleSeries(key: SeriesKey) {
        state = state.copy(hidden = if (key in state.hidden) state.hidden - key else state.hidden + key)
    }

    fun toggleTable() {
        state = state.copy(showTable = !state.showTable)
    }

    fun selectUnit(unit: ChartUnit) {
        state = state.copy(unit = unit)
    }

    fun dismissUnreadable() {
        state = state.copy(unreadable = emptyList())
    }
}
