package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.statistics.Totals
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.formatHoursAndMinutes
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.statistics_table_year
import ch.qscqlmpa.wingtracker.ui.resources.statistics_totals_before_tracking
import ch.qscqlmpa.wingtracker.ui.resources.statistics_totals_flights
import ch.qscqlmpa.wingtracker.ui.resources.statistics_totals_flights_column
import ch.qscqlmpa.wingtracker.ui.resources.statistics_totals_hours_column
import ch.qscqlmpa.wingtracker.ui.resources.statistics_totals_note
import ch.qscqlmpa.wingtracker.ui.resources.statistics_totals_note_search
import ch.qscqlmpa.wingtracker.ui.resources.statistics_totals_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The pilot's totals, every wing together and whatever the legend hides: overall first, then year by year. */
@Composable
fun TotalsCard(
    totals: Totals,
    theme: ChartTheme,
    // Under a search, the totals count only the matching flights, so the note must not claim all wings.
    searched: Boolean = false,
) {
    ChartCard(
        stringResource(Res.string.statistics_totals_title),
        stringResource(if (searched) Res.string.statistics_totals_note_search else Res.string.statistics_totals_note),
        theme,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Figure(
                    pluralStringResource(Res.plurals.statistics_totals_flights, totals.flights, totals.flights),
                    theme,
                )
                Figure(formatDuration(totals.hours), theme)
            }
            // The years cannot add up to the overall total when part of it has no date; this says where it comes from.
            if (totals.flightsBeforeTracking > 0 || totals.hoursBeforeTracking.isPositive()) {
                Text(
                    stringResource(
                        Res.string.statistics_totals_before_tracking,
                        totals.flightsBeforeTracking,
                        formatDuration(totals.hoursBeforeTracking),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textSecondary,
                )
            }
            Column(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row {
                    TableCell(stringResource(Res.string.statistics_table_year), FIRST_COLUMN, bold = true)
                    TableCell(stringResource(Res.string.statistics_totals_flights_column), COLUMN, bold = true)
                    TableCell(stringResource(Res.string.statistics_totals_hours_column), COLUMN, bold = true)
                }
                totals.years.forEach { year ->
                    Row {
                        TableCell(year.year.toString(), FIRST_COLUMN)
                        TableCell(year.flights.toString(), COLUMN)
                        TableCell(formatHoursAndMinutes(year.hours.inWholeMinutes), COLUMN)
                    }
                }
            }
        }
    }
}

@Composable
private fun Figure(
    text: String,
    theme: ChartTheme,
) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = theme.textPrimary)
}
