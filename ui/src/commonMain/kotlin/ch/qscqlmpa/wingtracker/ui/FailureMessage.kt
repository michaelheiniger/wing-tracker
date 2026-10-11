package ch.qscqlmpa.wingtracker.ui

import androidx.compose.runtime.Composable
import ch.qscqlmpa.wingtracker.ui.gear.wing.WingFormError
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.error_amount
import ch.qscqlmpa.wingtracker.ui.resources.error_currency
import ch.qscqlmpa.wingtracker.ui.resources.error_hours
import ch.qscqlmpa.wingtracker.ui.resources.error_required
import ch.qscqlmpa.wingtracker.ui.resources.failure_delete
import ch.qscqlmpa.wingtracker.ui.resources.failure_delete_flight
import ch.qscqlmpa.wingtracker.ui.resources.failure_delete_in_use
import ch.qscqlmpa.wingtracker.ui.resources.failure_load
import ch.qscqlmpa.wingtracker.ui.resources.failure_load_logbook
import ch.qscqlmpa.wingtracker.ui.resources.failure_load_statistics
import ch.qscqlmpa.wingtracker.ui.resources.failure_retire
import ch.qscqlmpa.wingtracker.ui.resources.failure_save
import ch.qscqlmpa.wingtracker.ui.resources.failure_save_flight
import org.jetbrains.compose.resources.stringResource

@Composable
fun Failure.message(): String {
    val reason = detail.orEmpty()
    return when (kind) {
        FailureKind.Load -> stringResource(Res.string.failure_load, reason)
        FailureKind.Save -> stringResource(Res.string.failure_save, reason)
        FailureKind.Retire -> stringResource(Res.string.failure_retire, reason)
        FailureKind.Delete -> stringResource(Res.string.failure_delete, reason)
        FailureKind.DeleteInUse -> stringResource(Res.string.failure_delete_in_use)
        FailureKind.LoadLogbook -> stringResource(Res.string.failure_load_logbook, reason)
        FailureKind.LoadStatistics -> stringResource(Res.string.failure_load_statistics, reason)
        FailureKind.SaveFlight -> stringResource(Res.string.failure_save_flight, reason)
        FailureKind.DeleteFlight -> stringResource(Res.string.failure_delete_flight, reason)
    }
}

@Composable
fun WingFormError.message(): String =
    when (this) {
        WingFormError.Required -> stringResource(Res.string.error_required)
        WingFormError.InvalidAmount -> stringResource(Res.string.error_amount)
        WingFormError.InvalidCurrency -> stringResource(Res.string.error_currency)
        WingFormError.InvalidHours -> stringResource(Res.string.error_hours)
    }
