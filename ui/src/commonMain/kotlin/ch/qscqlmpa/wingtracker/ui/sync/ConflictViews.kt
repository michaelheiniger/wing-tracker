package ch.qscqlmpa.wingtracker.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ch.qscqlmpa.wingtracker.sync.ConflictChoice
import ch.qscqlmpa.wingtracker.sync.ConflictSide
import ch.qscqlmpa.wingtracker.sync.RecordKind
import ch.qscqlmpa.wingtracker.sync.SyncConflict
import ch.qscqlmpa.wingtracker.ui.components.AppAlertDialog
import ch.qscqlmpa.wingtracker.ui.components.AppCard
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.flight_date_time
import ch.qscqlmpa.wingtracker.ui.resources.sync_apply
import ch.qscqlmpa.wingtracker.ui.resources.sync_conflict_change
import ch.qscqlmpa.wingtracker.ui.resources.sync_conflict_deleted
import ch.qscqlmpa.wingtracker.ui.resources.sync_conflict_difference
import ch.qscqlmpa.wingtracker.ui.resources.sync_conflict_here
import ch.qscqlmpa.wingtracker.ui.resources.sync_conflict_missing
import ch.qscqlmpa.wingtracker.ui.resources.sync_conflict_record
import ch.qscqlmpa.wingtracker.ui.resources.sync_conflict_there
import ch.qscqlmpa.wingtracker.ui.resources.sync_conflicts_intro
import ch.qscqlmpa.wingtracker.ui.resources.sync_conflicts_more
import ch.qscqlmpa.wingtracker.ui.resources.sync_conflicts_title
import ch.qscqlmpa.wingtracker.ui.resources.sync_delete_it
import ch.qscqlmpa.wingtracker.ui.resources.sync_in_progress
import ch.qscqlmpa.wingtracker.ui.resources.sync_keep_here
import ch.qscqlmpa.wingtracker.ui.resources.sync_keep_it
import ch.qscqlmpa.wingtracker.ui.resources.sync_keep_there
import ch.qscqlmpa.wingtracker.ui.resources.sync_kind_attachment
import ch.qscqlmpa.wingtracker.ui.resources.sync_kind_flight
import ch.qscqlmpa.wingtracker.ui.resources.sync_kind_harness
import ch.qscqlmpa.wingtracker.ui.resources.sync_kind_other
import ch.qscqlmpa.wingtracker.ui.resources.sync_kind_reserve
import ch.qscqlmpa.wingtracker.ui.resources.sync_kind_settings
import ch.qscqlmpa.wingtracker.ui.resources.sync_kind_wing
import ch.qscqlmpa.wingtracker.ui.toDisplay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Lists the records changed on two computers, each with what differs and a choice of version to keep. */
@Composable
fun ConflictDialog(presenter: ConflictPresenter) {
    val conflicts = presenter.conflicts ?: return
    // Not closed by a click beside it, which would throw away the choices made so far.
    AppAlertDialog(
        onDismissRequest = presenter::close,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(Res.string.sync_conflicts_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(
                        if (presenter.moreFound) Res.string.sync_conflicts_more else Res.string.sync_conflicts_intro,
                    ),
                )
                conflicts.forEach { ConflictCard(it, presenter) }
                if (presenter.busy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(Res.string.sync_in_progress))
                    }
                }
                presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = presenter::apply, enabled = presenter.complete && !presenter.busy) {
                Text(stringResource(Res.string.sync_apply))
            }
        },
        dismissButton = { TextButton(onClick = presenter::close) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun ConflictCard(
    conflict: SyncConflict,
    presenter: ConflictPresenter,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    Res.string.sync_conflict_record,
                    stringResource(conflict.record.kind.label()),
                    conflict.record.id,
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Side(Res.string.sync_conflict_here, conflict.here)
            Side(Res.string.sync_conflict_there, conflict.there)
            // The field names are the stored ones: technical, but the same for every kind of record.
            conflict.differences.forEach {
                Text(
                    stringResource(
                        Res.string.sync_conflict_difference,
                        it.field,
                        it.here ?: stringResource(Res.string.sync_conflict_missing),
                        it.there ?: stringResource(Res.string.sync_conflict_missing),
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (keepHere, keepThere) = conflict.choiceLabels()
                FilterChip(
                    selected = presenter.choices[conflict.record] == ConflictChoice.Here,
                    onClick = { presenter.choose(conflict.record, ConflictChoice.Here) },
                    label = { Text(stringResource(keepHere)) },
                )
                FilterChip(
                    selected = presenter.choices[conflict.record] == ConflictChoice.There,
                    onClick = { presenter.choose(conflict.record, ConflictChoice.There) },
                    label = { Text(stringResource(keepThere)) },
                )
            }
        }
    }
}

@Composable
private fun Side(
    label: StringResource,
    side: ConflictSide,
) {
    val change =
        side.change?.let {
            val at = it.at.toLocalDateTime(TimeZone.currentSystemDefault())
            stringResource(
                Res.string.sync_conflict_change,
                it.author,
                stringResource(Res.string.flight_date_time, at.date.toDisplay(), at.time.toDisplay()),
                it.description,
            )
        }
    val text = if (side.deleted) stringResource(Res.string.sync_conflict_deleted) else change.orEmpty()
    Text("${stringResource(label)}: $text", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** What keeping each side means when one of them deleted the record. */
private fun SyncConflict.choiceLabels(): Pair<StringResource, StringResource> =
    when {
        here.deleted -> Res.string.sync_delete_it to Res.string.sync_keep_it
        there.deleted -> Res.string.sync_keep_it to Res.string.sync_delete_it
        else -> Res.string.sync_keep_here to Res.string.sync_keep_there
    }

private fun RecordKind.label(): StringResource =
    when (this) {
        RecordKind.Wing -> Res.string.sync_kind_wing
        RecordKind.Harness -> Res.string.sync_kind_harness
        RecordKind.Reserve -> Res.string.sync_kind_reserve
        RecordKind.Flight -> Res.string.sync_kind_flight
        RecordKind.Attachment -> Res.string.sync_kind_attachment
        RecordKind.LogbookSettings -> Res.string.sync_kind_settings
        RecordKind.Other -> Res.string.sync_kind_other
    }
