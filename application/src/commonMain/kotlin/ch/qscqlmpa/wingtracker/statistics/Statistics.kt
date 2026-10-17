package ch.qscqlmpa.wingtracker.statistics

import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
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

data class YearlyFlights(
    val year: Int,
    val counts: Map<SeriesKey, Int>,
)

data class CumulativePoint(
    val date: LocalDate,
    val total: Duration,
)

data class CumulativeFlightsPoint(
    val date: LocalDate,
    val total: Int,
)

/** What an active wing has flown since its latest check; [check] is null when it never had one. */
data class SinceLastCheck(
    val wing: Wing,
    val check: MaintenanceCheck?,
    val flights: Int,
    val hours: Duration,
)

/** The flights and hours of one year, all wings together. */
data class YearTotal(
    val year: Int,
    val flights: Int,
    val hours: Duration,
)

/** All wings together, retired ones included: the pilot's own totals, whatever the charts show. */
data class Totals(
    // Logged flights only, like [Statistics.yearly].
    val years: List<YearTotal>,
    // Entered on the wings without a date, so they count in the overall total but in no year.
    val flightsBeforeTracking: Int,
    val hoursBeforeTracking: Duration,
) {
    val flights: Int get() = years.sumOf { it.flights } + flightsBeforeTracking
    val hours: Duration get() = years.fold(hoursBeforeTracking) { sum, year -> sum + year.hours }
}

data class Statistics(
    // Order is stable (by entering service) so a series keeps its color when others are hidden or added.
    val series: List<Series>,
    // The series to chart: every one, or under a search only those it found flights for.
    val shownSeries: List<Series>,
    // Tracked flights only: hours before tracking carry no date, so they cannot belong to a year.
    val yearly: List<YearlyHours>,
    // Includes hours flown before tracking, because that is what the wing really has on the clock.
    val cumulative: Map<SeriesKey, List<CumulativePoint>>,
    // Tracked flights only, for the same reason as [yearly].
    val yearlyFlights: List<YearlyFlights>,
    // Includes flights made before tracking, like [cumulative] does for hours.
    val cumulativeFlights: Map<SeriesKey, List<CumulativeFlightsPoint>>,
    val sinceLastCheck: List<SinceLastCheck>,
    val totals: Totals,
    // The last day the charts cover: today, or the end of the search when that is earlier.
    val until: LocalDate,
    val unreadable: List<UnreadableRecord>,
) {
    val isEmpty: Boolean get() = yearly.isEmpty()

    /** Cumulative hours are flat between flights, so the value on a date is the last point at or before it. */
    fun cumulativeAt(
        key: SeriesKey,
        date: LocalDate,
    ): Duration = cumulative[key].orEmpty().lastOrNull { it.date <= date }?.total ?: Duration.ZERO

    fun cumulativeFlightsAt(
        key: SeriesKey,
        date: LocalDate,
    ): Int = cumulativeFlights[key].orEmpty().lastOrNull { it.date <= date }?.total ?: 0
}
