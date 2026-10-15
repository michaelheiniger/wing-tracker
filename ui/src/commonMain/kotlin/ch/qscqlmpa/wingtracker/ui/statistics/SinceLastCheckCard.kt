package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.statistics.SinceLastCheck
import ch.qscqlmpa.wingtracker.ui.formatHoursAndMinutes
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.label
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.statistics_since_check_flights
import ch.qscqlmpa.wingtracker.ui.resources.statistics_since_check_hours
import ch.qscqlmpa.wingtracker.ui.resources.statistics_since_check_last
import ch.qscqlmpa.wingtracker.ui.resources.statistics_since_check_none
import ch.qscqlmpa.wingtracker.ui.resources.statistics_since_check_note
import ch.qscqlmpa.wingtracker.ui.resources.statistics_since_check_title
import ch.qscqlmpa.wingtracker.ui.resources.statistics_since_check_wing
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.stringResource

@Composable
fun SinceLastCheckCard(
    rows: List<SinceLastCheck>,
    theme: ChartTheme,
) {
    if (rows.isEmpty()) return
    ChartCard(
        stringResource(Res.string.statistics_since_check_title),
        stringResource(Res.string.statistics_since_check_note),
        theme,
    ) {
        Column(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row {
                TableCell(stringResource(Res.string.statistics_since_check_wing), WIDE_COLUMN, bold = true)
                TableCell(stringResource(Res.string.statistics_since_check_last), WIDE_COLUMN, bold = true)
                TableCell(stringResource(Res.string.statistics_since_check_flights), COLUMN, bold = true)
                TableCell(stringResource(Res.string.statistics_since_check_hours), COLUMN, bold = true)
            }
            rows.forEach { row ->
                Row {
                    TableCell(row.wing.details.name, WIDE_COLUMN)
                    val check = row.check
                    if (check == null) {
                        TableCell(stringResource(Res.string.statistics_since_check_none), WIDE_COLUMN)
                        TableCell("", COLUMN)
                        TableCell("", COLUMN)
                    } else {
                        TableCell("${check.date.toDisplay()} · ${check.type.label()}", WIDE_COLUMN)
                        TableCell(row.flights.toString(), COLUMN)
                        TableCell(formatHoursAndMinutes(row.hours.inWholeMinutes), COLUMN)
                    }
                }
            }
        }
    }
}
