package ch.qscqlmpa.wingtracker.ui.flights.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.flights.SiteNames
import ch.qscqlmpa.wingtracker.flights.SiteSource
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.components.StatusBadge
import ch.qscqlmpa.wingtracker.ui.flights.label
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.flight_detail_site_other
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A site's shown name with where it came from, and below it what the other sources call it (ADR 0030). */
@Composable
internal fun SiteFact(
    label: StringResource,
    names: SiteNames,
) {
    Fact(label, names.shown)
    val shownSource = names.shownSource ?: return
    Column(
        modifier = Modifier.padding(start = FACT_VALUE_INDENT),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatusBadge(shownSource.label(), Severity.Neutral)
        SiteSource.entries.filter { it != shownSource }.forEach { source ->
            names[source]?.let { name ->
                Text(
                    stringResource(Res.string.flight_detail_site_other, source.label(), name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
