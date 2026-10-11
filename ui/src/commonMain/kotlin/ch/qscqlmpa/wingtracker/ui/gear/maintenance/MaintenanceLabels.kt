package ch.qscqlmpa.wingtracker.ui.gear.maintenance

import androidx.compose.runtime.Composable
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceStatus
import ch.qscqlmpa.wingtracker.ui.formatDuration
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.check_type_check
import ch.qscqlmpa.wingtracker.ui.resources.check_type_full
import ch.qscqlmpa.wingtracker.ui.resources.check_type_other
import ch.qscqlmpa.wingtracker.ui.resources.check_type_partial
import ch.qscqlmpa.wingtracker.ui.resources.check_type_repack
import ch.qscqlmpa.wingtracker.ui.resources.error_check_date_future
import ch.qscqlmpa.wingtracker.ui.resources.error_plan_date_past
import ch.qscqlmpa.wingtracker.ui.resources.error_required
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_due
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_hours_left
import ch.qscqlmpa.wingtracker.ui.resources.maintenance_next_by
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.stringResource

@Composable
fun CheckType.label(): String =
    stringResource(
        when (this) {
            CheckType.Partial -> Res.string.check_type_partial
            CheckType.Full -> Res.string.check_type_full
            CheckType.Check -> Res.string.check_type_check
            CheckType.Repack -> Res.string.check_type_repack
            CheckType.Other -> Res.string.check_type_other
        },
    )

@Composable
fun CheckFormError.message(): String =
    when (this) {
        CheckFormError.Required -> stringResource(Res.string.error_required)
        CheckFormError.DateInFuture -> stringResource(Res.string.error_check_date_future)
    }

@Composable
fun PlanFormError.message(): String =
    when (this) {
        PlanFormError.Required -> stringResource(Res.string.error_required)
        PlanFormError.DateInPast -> stringResource(Res.string.error_plan_date_past)
    }

/** One line for the status chip: "Check due", or when the next check is expected by date and/or hours. */
@Composable
fun MaintenanceStatus.chipText(): String =
    if (isDue) {
        stringResource(Res.string.maintenance_due)
    } else {
        listOfNotNull(
            dueDate?.let { stringResource(Res.string.maintenance_next_by, it.toDisplay()) },
            hoursRemaining?.let { stringResource(Res.string.maintenance_hours_left, formatDuration(it)) },
        ).joinToString(" · ")
    }
