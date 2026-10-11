package ch.qscqlmpa.wingtracker.statistics

import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/**
 * Charts can tell this many entities apart by color; wings beyond it are folded into [Other]
 * instead of inventing hues that readers could not distinguish.
 */
const val MAX_NAMED_SERIES = 8

sealed interface SeriesKey {
    data class OfWing(
        val id: WingId,
    ) : SeriesKey

    data object Other : SeriesKey
}

/** [wings] has one wing for a named series and several for [SeriesKey.Other]. */
data class Series(
    val key: SeriesKey,
    val wings: List<Wing>,
)

data class YearlyHours(
    val year: Int,
    val hours: Map<SeriesKey, Duration>,
)

data class CumulativePoint(
    val date: LocalDate,
    val total: Duration,
)

data class Statistics(
    // Order is stable (by entering service) so a series keeps its color when others are hidden or added.
    val series: List<Series>,
    // Tracked flights only: hours before tracking carry no date, so they cannot belong to a year.
    val yearly: List<YearlyHours>,
    // Includes hours flown before tracking, because that is what the wing really has on the clock.
    val cumulative: Map<SeriesKey, List<CumulativePoint>>,
    val today: LocalDate,
    val unreadable: List<UnreadableRecord>,
) {
    val isEmpty: Boolean get() = yearly.isEmpty()

    /** Cumulative hours are flat between flights, so the value on a date is the last point at or before it. */
    fun cumulativeAt(
        key: SeriesKey,
        date: LocalDate,
    ): Duration = cumulative[key].orEmpty().lastOrNull { it.date <= date }?.total ?: Duration.ZERO
}
