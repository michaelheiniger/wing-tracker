package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.flights.igc.MatchReason
import ch.qscqlmpa.wingtracker.flights.igc.TrackMatch
import ch.qscqlmpa.wingtracker.ui.components.HelpTooltip
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.igc_choice_attach
import ch.qscqlmpa.wingtracker.ui.resources.igc_choice_new
import ch.qscqlmpa.wingtracker.ui.resources.igc_filter_all
import ch.qscqlmpa.wingtracker.ui.resources.igc_filter_exact
import ch.qscqlmpa.wingtracker.ui.resources.igc_filter_none
import ch.qscqlmpa.wingtracker.ui.resources.igc_filter_possible
import ch.qscqlmpa.wingtracker.ui.resources.igc_linking_intro
import ch.qscqlmpa.wingtracker.ui.resources.igc_linking_possible
import ch.qscqlmpa.wingtracker.ui.resources.igc_linking_syride
import ch.qscqlmpa.wingtracker.ui.resources.igc_linking_time
import ch.qscqlmpa.wingtracker.ui.resources.igc_linking_title
import ch.qscqlmpa.wingtracker.ui.resources.igc_linking_zone
import ch.qscqlmpa.wingtracker.ui.resources.igc_match_exact
import ch.qscqlmpa.wingtracker.ui.resources.igc_match_possible
import ch.qscqlmpa.wingtracker.ui.resources.igc_match_why_possible
import ch.qscqlmpa.wingtracker.ui.resources.igc_match_why_syride
import ch.qscqlmpa.wingtracker.ui.resources.igc_match_why_time
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// How the track review shows the flight a track belongs to (ADR 0026).

private const val MATCH_HELP_ICON_DP = 16

/** How many tracks belong to a flight, might, or do not; each count shows only those rows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatchSummary(presenter: IgcBatchPresenter) {
    val exact = presenter.rows.countOf(TrackMatchKind.Exact)
    val possible = presenter.rows.countOf(TrackMatchKind.Possible)
    // Without any match there is nothing to tell apart, as for a pilot who never imported from Syride.
    if (exact + possible == 0) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = presenter.filter == null,
            onClick = { presenter.showOnly(null) },
            label = { Text(stringResource(Res.string.igc_filter_all, presenter.rows.size)) },
        )
        TrackMatchKind.entries.forEach { kind ->
            val count = presenter.rows.countOf(kind)
            if (count > 0) {
                FilterChip(
                    selected = presenter.filter == kind,
                    onClick = { presenter.showOnly(kind) },
                    label = { Text(pluralStringResource(kind.label(), count, count)) },
                )
            }
        }
    }
}

private fun TrackMatchKind.label(): PluralStringResource =
    when (this) {
        TrackMatchKind.Exact -> Res.plurals.igc_filter_exact
        TrackMatchKind.Possible -> Res.plurals.igc_filter_possible
        TrackMatchKind.None -> Res.plurals.igc_filter_none
    }

/** The flight a track belongs to, with why the review thinks so. */
@Composable
internal fun MatchLine(
    match: TrackMatch,
    row: IgcBatchRow,
) {
    val details = match.flight.details
    val flight =
        listOfNotNull(
            details.date.toDisplay(),
            details.takeOffTime?.toDisplay(),
            details.takeOffSite,
            formatDuration(details.duration),
        ).joinToString(" · ")
    val why =
        when (match) {
            is TrackMatch.Exact -> {
                when (match.reason) {
                    MatchReason.SyrideId -> {
                        stringResource(Res.string.igc_match_why_syride, row.file.fileName.substringBeforeLast('.'))
                    }

                    MatchReason.TakeOffTime -> {
                        stringResource(Res.string.igc_match_why_time, details.takeOffTime?.toDisplay().orEmpty())
                    }
                }
            }

            is TrackMatch.Possible -> {
                stringResource(Res.string.igc_match_why_possible)
            }
        }
    val label = if (match is TrackMatch.Exact) Res.string.igc_match_exact else Res.string.igc_match_possible
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label, flight), style = MaterialTheme.typography.bodyMedium)
        HelpTooltip(why, iconSize = MATCH_HELP_ICON_DP)
    }
}

@Composable
internal fun AttachChoice(
    row: IgcBatchRow,
    presenter: IgcBatchPresenter,
) {
    val choices = listOf(true to Res.string.igc_choice_attach, false to Res.string.igc_choice_new)
    SingleChoiceSegmentedButtonRow {
        choices.forEachIndexed { index, (attach, label) ->
            SegmentedButton(
                selected = row.attach == attach,
                onClick = { presenter.choose(row, attach) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                enabled = !presenter.importing,
            ) {
                Text(stringResource(label))
            }
        }
    }
}

/** How the review decides which flight a track belongs to; folded away, since it matters mostly when one is missing. */
@Composable
internal fun LinkingHelp() {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) {
        Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        Text(stringResource(Res.string.igc_linking_title))
    }
    if (!open) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.igc_linking_intro))
            listOf(Res.string.igc_linking_syride, Res.string.igc_linking_time, Res.string.igc_linking_possible)
                .forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodySmall) }
            Text(
                stringResource(Res.string.igc_linking_zone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
