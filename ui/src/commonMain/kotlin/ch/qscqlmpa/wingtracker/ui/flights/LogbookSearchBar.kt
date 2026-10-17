package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.ui.components.DateField
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.logbook_search_clear
import ch.qscqlmpa.wingtracker.ui.resources.logbook_search_clear_text
import ch.qscqlmpa.wingtracker.ui.resources.logbook_search_from
import ch.qscqlmpa.wingtracker.ui.resources.logbook_search_label
import ch.qscqlmpa.wingtracker.ui.resources.logbook_search_placeholder
import ch.qscqlmpa.wingtracker.ui.resources.logbook_search_to
import org.jetbrains.compose.resources.stringResource

/** Text, date range and wings narrow the logbook together; each is visible, so a forgotten filter is easy to spot. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogbookSearchBar(
    search: LogbookSearch,
    wings: List<Wing>,
    onChange: (LogbookSearch) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = search.text,
                onValueChange = { onChange(search.copy(text = it)) },
                label = { Text(stringResource(Res.string.logbook_search_label)) },
                placeholder = { Text(stringResource(Res.string.logbook_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.text.isNotEmpty()) {
                        IconButton(onClick = { onChange(search.copy(text = "")) }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(Res.string.logbook_search_clear_text),
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            DateField(
                label = stringResource(Res.string.logbook_search_from),
                value = search.from,
                onValueChange = { onChange(search.copy(from = it)) },
                clearable = true,
                modifier = Modifier.width(DATE_FIELD_WIDTH),
            )
            DateField(
                label = stringResource(Res.string.logbook_search_to),
                value = search.to,
                onValueChange = { onChange(search.copy(to = it)) },
                clearable = true,
                modifier = Modifier.width(DATE_FIELD_WIDTH),
            )
        }
        // A single wing would always be selected, so the chips only help once there is a choice.
        if (wings.size > 1 || !search.isBlank) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (wings.size > 1) {
                    wings.forEach { wing ->
                        val selected = wing.id in search.wingIds
                        FilterChip(
                            selected = selected,
                            onClick = { onChange(search.toggle(wing.id)) },
                            label = { Text(wing.details.name) },
                            leadingIcon =
                                if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                        )
                    }
                }
                if (!search.isBlank) {
                    TextButton(onClick = { onChange(LogbookSearch()) }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                        Text(stringResource(Res.string.logbook_search_clear))
                    }
                }
            }
        }
    }
}

private val DATE_FIELD_WIDTH = 200.dp
