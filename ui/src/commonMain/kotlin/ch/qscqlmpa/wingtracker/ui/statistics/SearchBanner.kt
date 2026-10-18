package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.flights.FlightFilter
import ch.qscqlmpa.wingtracker.flights.FlightType
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.ui.flights.label
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.statistics_search_banner
import ch.qscqlmpa.wingtracker.ui.resources.statistics_search_from
import ch.qscqlmpa.wingtracker.ui.resources.statistics_search_range
import ch.qscqlmpa.wingtracker.ui.resources.statistics_search_show_all
import ch.qscqlmpa.wingtracker.ui.resources.statistics_search_text
import ch.qscqlmpa.wingtracker.ui.resources.statistics_search_until
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.stringResource

/** Says the figures cover only a search, and what it was, so they are never mistaken for the whole logbook. */
@Composable
internal fun SearchBanner(
    filter: FlightFilter,
    wings: List<Wing>,
    onShowAll: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                stringResource(Res.string.statistics_search_banner, describe(filter, wings)),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShowAll) { Text(stringResource(Res.string.statistics_search_show_all)) }
        }
    }
}

@Composable
private fun describe(
    filter: FlightFilter,
    wings: List<Wing>,
): String {
    val from = filter.from?.toDisplay()
    val to = filter.to?.toDisplay()
    val parts =
        listOfNotNull(
            filter.text.trim().takeIf { it.isNotEmpty() }?.let {
                stringResource(
                    Res.string.statistics_search_text,
                    it,
                )
            },
            wings
                .filter { it.id in filter.wingIds }
                .joinToString(", ") { it.details.name }
                .takeIf { it.isNotEmpty() },
            FlightType.entries
                .filter { it in filter.types }
                .map { it.label() }
                .joinToString(", ")
                .takeIf { it.isNotEmpty() },
            when {
                from != null && to != null -> stringResource(Res.string.statistics_search_range, from, to)
                from != null -> stringResource(Res.string.statistics_search_from, from)
                to != null -> stringResource(Res.string.statistics_search_until, to)
                else -> null
            },
        )
    return parts.joinToString(" · ")
}
