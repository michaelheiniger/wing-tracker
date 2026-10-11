package ch.qscqlmpa.wingtracker.ui

import androidx.compose.runtime.Composable
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.error_amount
import ch.qscqlmpa.wingtracker.ui.resources.error_currency
import ch.qscqlmpa.wingtracker.ui.resources.error_days
import ch.qscqlmpa.wingtracker.ui.resources.error_hours
import ch.qscqlmpa.wingtracker.ui.resources.error_months
import ch.qscqlmpa.wingtracker.ui.resources.error_required
import ch.qscqlmpa.wingtracker.ui.resources.failure_cancel_plan
import ch.qscqlmpa.wingtracker.ui.resources.failure_change_data_folder
import ch.qscqlmpa.wingtracker.ui.resources.failure_data_overlaps
import ch.qscqlmpa.wingtracker.ui.resources.failure_data_same
import ch.qscqlmpa.wingtracker.ui.resources.failure_data_unusable
import ch.qscqlmpa.wingtracker.ui.resources.failure_delete
import ch.qscqlmpa.wingtracker.ui.resources.failure_delete_check
import ch.qscqlmpa.wingtracker.ui.resources.failure_delete_flight
import ch.qscqlmpa.wingtracker.ui.resources.failure_delete_in_use
import ch.qscqlmpa.wingtracker.ui.resources.failure_drop_no_igc
import ch.qscqlmpa.wingtracker.ui.resources.failure_export_calendar
import ch.qscqlmpa.wingtracker.ui.resources.failure_import_igc
import ch.qscqlmpa.wingtracker.ui.resources.failure_load
import ch.qscqlmpa.wingtracker.ui.resources.failure_load_attention
import ch.qscqlmpa.wingtracker.ui.resources.failure_load_logbook
import ch.qscqlmpa.wingtracker.ui.resources.failure_load_maintenance
import ch.qscqlmpa.wingtracker.ui.resources.failure_load_settings
import ch.qscqlmpa.wingtracker.ui.resources.failure_load_statistics
import ch.qscqlmpa.wingtracker.ui.resources.failure_open_data_folder
import ch.qscqlmpa.wingtracker.ui.resources.failure_open_report
import ch.qscqlmpa.wingtracker.ui.resources.failure_open_track
import ch.qscqlmpa.wingtracker.ui.resources.failure_retire
import ch.qscqlmpa.wingtracker.ui.resources.failure_save
import ch.qscqlmpa.wingtracker.ui.resources.failure_save_check
import ch.qscqlmpa.wingtracker.ui.resources.failure_save_flight
import ch.qscqlmpa.wingtracker.ui.resources.failure_save_plan
import ch.qscqlmpa.wingtracker.ui.resources.failure_save_settings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// A table rather than a long `when`: the kinds only differ by which text they use.
private val FAILURE_TEXTS: Map<FailureKind, StringResource> =
    mapOf(
        FailureKind.LoadAttention to Res.string.failure_load_attention,
        FailureKind.ExportCalendar to Res.string.failure_export_calendar,
        FailureKind.ImportIgc to Res.string.failure_import_igc,
        FailureKind.OpenTrack to Res.string.failure_open_track,
        FailureKind.DropNoIgc to Res.string.failure_drop_no_igc,
        FailureKind.OpenDataFolder to Res.string.failure_open_data_folder,
        FailureKind.ChangeDataFolder to Res.string.failure_change_data_folder,
        FailureKind.DataFolderSame to Res.string.failure_data_same,
        FailureKind.DataFolderUnusable to Res.string.failure_data_unusable,
        FailureKind.DataFolderOverlaps to Res.string.failure_data_overlaps,
        FailureKind.SaveSettings to Res.string.failure_save_settings,
        FailureKind.LoadSettings to Res.string.failure_load_settings,
        FailureKind.Load to Res.string.failure_load,
        FailureKind.Save to Res.string.failure_save,
        FailureKind.Retire to Res.string.failure_retire,
        FailureKind.Delete to Res.string.failure_delete,
        FailureKind.LoadLogbook to Res.string.failure_load_logbook,
        FailureKind.SaveFlight to Res.string.failure_save_flight,
        FailureKind.DeleteFlight to Res.string.failure_delete_flight,
        FailureKind.LoadStatistics to Res.string.failure_load_statistics,
        FailureKind.LoadMaintenance to Res.string.failure_load_maintenance,
        FailureKind.SaveCheck to Res.string.failure_save_check,
        FailureKind.DeleteCheck to Res.string.failure_delete_check,
        FailureKind.OpenReport to Res.string.failure_open_report,
        FailureKind.SavePlan to Res.string.failure_save_plan,
        FailureKind.CancelPlan to Res.string.failure_cancel_plan,
    )

@Composable
fun Failure.message(): String =
    when (val text = FAILURE_TEXTS[kind]) {
        // Only DeleteInUse has no technical detail to show.
        null -> stringResource(Res.string.failure_delete_in_use)

        else -> stringResource(text, detail.orEmpty())
    }

@Composable
fun FormError.message(): String =
    when (this) {
        FormError.Required -> stringResource(Res.string.error_required)
        FormError.InvalidAmount -> stringResource(Res.string.error_amount)
        FormError.InvalidCurrency -> stringResource(Res.string.error_currency)
        FormError.InvalidHours -> stringResource(Res.string.error_hours)
        FormError.InvalidMonths -> stringResource(Res.string.error_months)
        FormError.InvalidDays -> stringResource(Res.string.error_days)
    }
