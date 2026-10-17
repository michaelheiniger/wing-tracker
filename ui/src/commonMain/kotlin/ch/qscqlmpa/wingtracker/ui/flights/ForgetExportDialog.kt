package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.airtime.AIRTIME_SYSTEM
import ch.qscqlmpa.wingtracker.ui.components.AppAlertDialog
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.forget_export_body
import ch.qscqlmpa.wingtracker.ui.resources.forget_export_confirm
import ch.qscqlmpa.wingtracker.ui.resources.forget_export_title
import ch.qscqlmpa.wingtracker.ui.resources.undo_export_forgotten
import ch.qscqlmpa.wingtracker.ui.toDisplay
import ch.qscqlmpa.wingtracker.ui.undo.LocalUndo
import ch.qscqlmpa.wingtracker.ui.undo.undoText
import org.jetbrains.compose.resources.stringResource

/** Asks before forgetting the export to air-time; the record can still be taken back until the undo expires. */
@Composable
internal fun ForgetExportDialog(
    flight: Flight,
    wingName: String,
    presenter: LogbookPresenter,
    onDismiss: () -> Unit,
) {
    val undo = LocalUndo.current
    val undoText = undoText(Res.string.undo_export_forgotten)
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.forget_export_title)) },
        text = { Text(stringResource(Res.string.forget_export_body, flight.details.date.toDisplay(), wingName)) },
        confirmButton = {
            Button(
                onClick = {
                    presenter.forgetExport(flight.id, AIRTIME_SYSTEM, undo, undoText)
                    onDismiss()
                },
            ) { Text(stringResource(Res.string.forget_export_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
