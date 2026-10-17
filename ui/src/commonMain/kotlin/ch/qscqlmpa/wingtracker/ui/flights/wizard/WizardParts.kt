package ch.qscqlmpa.wingtracker.ui.flights.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.components.HelpTooltip
import ch.qscqlmpa.wingtracker.ui.flights.importing.Buttons
import ch.qscqlmpa.wingtracker.ui.flights.importing.Hint
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.import_menu_needs_wing
import ch.qscqlmpa.wingtracker.ui.resources.import_source_device
import ch.qscqlmpa.wingtracker.ui.resources.import_source_device_text
import ch.qscqlmpa.wingtracker.ui.resources.import_source_igc
import ch.qscqlmpa.wingtracker.ui.resources.import_source_igc_text
import ch.qscqlmpa.wingtracker.ui.resources.import_source_spreadsheet
import ch.qscqlmpa.wingtracker.ui.resources.import_source_spreadsheet_text
import ch.qscqlmpa.wingtracker.ui.resources.import_source_start_here
import ch.qscqlmpa.wingtracker.ui.resources.import_source_syride
import ch.qscqlmpa.wingtracker.ui.resources.import_source_syride_text
import ch.qscqlmpa.wingtracker.ui.resources.import_step_account
import ch.qscqlmpa.wingtracker.ui.resources.import_step_columns
import ch.qscqlmpa.wingtracker.ui.resources.import_step_details
import ch.qscqlmpa.wingtracker.ui.resources.import_step_done
import ch.qscqlmpa.wingtracker.ui.resources.import_step_file
import ch.qscqlmpa.wingtracker.ui.resources.import_step_source
import ch.qscqlmpa.wingtracker.ui.resources.import_step_tracks
import ch.qscqlmpa.wingtracker.ui.resources.import_step_wings
import ch.qscqlmpa.wingtracker.ui.resources.import_tip
import ch.qscqlmpa.wingtracker.ui.resources.import_tip_why
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val STEP_MARK_DP = 24
private const val STEP_LINE_DP = 24

private enum class StepState { Done, Current, Upcoming }

/** The steps of the chosen source, so the pilot sees how far along the import is and what comes next. */
@Composable
internal fun Stepper(
    steps: List<ImportStep>,
    current: ImportStep,
) {
    val currentIndex = steps.indexOf(current)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            if (index > 0) HorizontalDivider(modifier = Modifier.width(STEP_LINE_DP.dp))
            val state =
                when {
                    index < currentIndex -> StepState.Done
                    index == currentIndex -> StepState.Current
                    else -> StepState.Upcoming
                }
            StepLabel(index + 1, stringResource(step.label()), state)
        }
    }
}

// A check mark, a filled number or an outlined number: the state shows without relying on colour.
@Composable
private fun StepLabel(
    number: Int,
    label: String,
    state: StepState,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val scheme = MaterialTheme.colorScheme
        val upcoming = state == StepState.Upcoming
        Surface(
            shape = CircleShape,
            color = if (upcoming) scheme.surface else scheme.primary,
            contentColor = if (upcoming) scheme.onSurfaceVariant else scheme.onPrimary,
            border = if (upcoming) BorderStroke(1.dp, scheme.outline) else null,
            modifier = Modifier.size(STEP_MARK_DP.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state == StepState.Done) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(4.dp))
                } else {
                    Text(number.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (state == StepState.Current) FontWeight.Bold else FontWeight.Normal,
            color = if (upcoming) scheme.onSurfaceVariant else scheme.onSurface,
        )
    }
}

private fun ImportStep.label(): StringResource =
    when (this) {
        ImportStep.Source -> Res.string.import_step_source
        ImportStep.Details -> Res.string.import_step_details
        ImportStep.Account -> Res.string.import_step_account
        ImportStep.File -> Res.string.import_step_file
        ImportStep.Columns -> Res.string.import_step_columns
        ImportStep.Wings -> Res.string.import_step_wings
        ImportStep.Tracks -> Res.string.import_step_tracks
        ImportStep.Done -> Res.string.import_step_done
    }

/** The sources as cards, with the advice to start with Syride, since tracks imported later attach to its flights. */
@Composable
internal fun SourceStep(
    presenter: ImportWizardPresenter,
    hasWings: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.import_tip), modifier = Modifier.weight(1f))
            HelpTooltip(stringResource(Res.string.import_tip_why))
        }
    }
    // A track is logged on a wing, like a flight entered by hand; Syride and spreadsheets create the wings they need.
    CardRow {
        SourceCard(
            Res.string.import_source_syride,
            Res.string.import_source_syride_text,
            onClick = presenter::chooseSyride,
            startHere = true,
        )
        SourceCard(
            Res.string.import_source_igc,
            Res.string.import_source_igc_text,
            onClick = presenter::chooseIgcFiles,
            needsWing = !hasWings,
        )
    }
    CardRow {
        SourceCard(
            Res.string.import_source_device,
            Res.string.import_source_device_text,
            onClick = presenter::chooseDevice,
            needsWing = !hasWings,
        )
        SourceCard(
            Res.string.import_source_spreadsheet,
            Res.string.import_source_spreadsheet_text,
            onClick = presenter::chooseSpreadsheet,
        )
    }
    presenter.pickFailure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    Buttons { TextButton(onClick = presenter::cancel) { Text(stringResource(Res.string.action_cancel)) } }
}

// Cards of one row share their height, so the grid stays even whatever the length of the texts.
@Composable
private fun CardRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun RowScope.SourceCard(
    title: StringResource,
    text: StringResource,
    onClick: () -> Unit,
    startHere: Boolean = false,
    needsWing: Boolean = false,
) {
    OutlinedCard(onClick = onClick, enabled = !needsWing, modifier = Modifier.weight(1f).fillMaxHeight()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                if (startHere) StartHere()
            }
            Hint(stringResource(text))
            // Said rather than only greyed out, so the pilot knows how to make it available.
            if (needsWing) {
                Text(stringResource(Res.string.import_menu_needs_wing), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StartHere() {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
        Text(
            stringResource(Res.string.import_source_start_here),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
