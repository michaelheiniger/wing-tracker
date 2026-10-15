package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.statistics.Statistics
import ch.qscqlmpa.wingtracker.ui.formatHoursAndMinutes
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.label
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.statistics_all_wings
import ch.qscqlmpa.wingtracker.ui.resources.statistics_all_years
import ch.qscqlmpa.wingtracker.ui.resources.statistics_table_cumulative_flights_title
import ch.qscqlmpa.wingtracker.ui.resources.statistics_table_cumulative_title
import ch.qscqlmpa.wingtracker.ui.resources.statistics_table_year
import ch.qscqlmpa.wingtracker.ui.resources.statistics_table_yearly_flights_title
import ch.qscqlmpa.wingtracker.ui.resources.statistics_table_yearly_title
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

private const val LAST_DAY_OF_DECEMBER = 31

val FIRST_COLUMN = 72.dp
val COLUMN = 120.dp
val WIDE_COLUMN = 200.dp

@Composable
fun TableCell(
    text: String,
    width: Dp,
    bold: Boolean = false,
) = Text(
    text,
    modifier = Modifier.width(width),
    style = if (bold) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
)

/** The table view: every number the charts draw, readable without color or a pointer. */
@Composable
fun StatisticsTables(
    statistics: Statistics,
    series: List<ChartSeries>,
) {
    @Composable
    fun Table(
        title: String,
        rows: List<Pair<String, List<String>>>,
        withTotal: Boolean,
    ) {
        // The last column adds up every wing, hidden ones included, like the totals card.
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Column(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row {
                TableCell(stringResource(Res.string.statistics_table_year), FIRST_COLUMN, bold = true)
                series.forEach { TableCell(it.name, COLUMN, bold = true) }
                if (withTotal) TableCell(stringResource(Res.string.statistics_all_wings), COLUMN, bold = true)
            }
            rows.forEach { (label, values) ->
                Row {
                    TableCell(label, FIRST_COLUMN)
                    values.forEach { TableCell(it, COLUMN) }
                }
            }
        }
    }

    fun hours(value: Duration) = formatHoursAndMinutes(value.inWholeMinutes)

    // The current year ends today, because the lines do not extend into the future.
    fun yearEnd(year: Int) = minOf(LocalDate(year, Month.DECEMBER, LAST_DAY_OF_DECEMBER), statistics.today)

    val allWings = statistics.totals.years.associateBy { it.year }
    val allYears = stringResource(Res.string.statistics_all_years)
    val sum = { values: List<Duration> -> values.fold(Duration.ZERO, Duration::plus) }
    Table(
        title = stringResource(Res.string.statistics_table_yearly_title),
        rows =
            statistics.yearly.map { year ->
                val values = series.map { year.hours[it.key] ?: Duration.ZERO }
                year.year.toString() to values.map(::hours) + hours(allWings.getValue(year.year).hours)
            } +
                (
                    allYears to
                        series.map { s -> hours(sum(statistics.yearly.map { it.hours[s.key] ?: Duration.ZERO })) } +
                        hours(sum(statistics.totals.years.map { it.hours }))
                ),
        withTotal = true,
    )
    Table(
        title = stringResource(Res.string.statistics_table_yearly_flights_title),
        rows =
            statistics.yearlyFlights.map { year ->
                val values = series.map { year.counts[it.key] ?: 0 }
                year.year.toString() to values.map(Int::toString) + allWings.getValue(year.year).flights.toString()
            } +
                (
                    allYears to
                        series.map { s -> statistics.yearlyFlights.sumOf { it.counts[s.key] ?: 0 }.toString() } +
                        statistics.totals.years
                            .sumOf { it.flights }
                            .toString()
                ),
        withTotal = true,
    )
    Table(
        title = stringResource(Res.string.statistics_table_cumulative_title),
        rows =
            statistics.yearly.map { year ->
                year.year.toString() to series.map { hours(statistics.cumulativeAt(it.key, yearEnd(year.year))) }
            },
        withTotal = false,
    )
    Table(
        title = stringResource(Res.string.statistics_table_cumulative_flights_title),
        rows =
            statistics.yearlyFlights.map { year ->
                year.year.toString() to
                    series.map { statistics.cumulativeFlightsAt(it.key, yearEnd(year.year)).toString() }
            },
        withTotal = false,
    )
}
