package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.Logbook
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.delete_confirm
import ch.qscqlmpa.wingtracker.ui.resources.flight_delete_body
import ch.qscqlmpa.wingtracker.ui.resources.flight_delete_title
import ch.qscqlmpa.wingtracker.ui.resources.flight_unknown_wing
import ch.qscqlmpa.wingtracker.ui.resources.undo_flight_deleted
import ch.qscqlmpa.wingtracker.ui.toDisplay
import ch.qscqlmpa.wingtracker.ui.undo.LocalUndo
import ch.qscqlmpa.wingtracker.ui.undo.undoText
import org.jetbrains.compose.resources.stringResource

/** Asks before deleting; the flight then disappears at once and can be taken back until the undo expires. */
@Composable
internal fun DeleteFlightDialog(
    flight: Flight,
    wingName: String,
    presenter: LogbookPresenter,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val undo = LocalUndo.current
    val undoText = undoText(Res.string.undo_flight_deleted)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.flight_delete_title)) },
        text = { Text(stringResource(Res.string.flight_delete_body, flight.details.date.toDisplay(), wingName)) },
        confirmButton = {
            Button(
                onClick = {
                    presenter.delete(flight.id, undo, undoText)
                    onDeleted()
                },
            ) { Text(stringResource(Res.string.delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

/** The name of the wing the flight was made with, also when that wing's file could not be read. */
@Composable
internal fun Logbook?.wingNameOf(flight: Flight): String =
    this
        ?.wings
        ?.firstOrNull { it.id == flight.details.wingId }
        ?.details
        ?.name
        ?: stringResource(Res.string.flight_unknown_wing)
