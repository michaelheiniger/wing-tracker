package ch.qscqlmpa.wingtracker.ui.reminders

import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.reminders.CalendarEntry
import ch.qscqlmpa.wingtracker.reminders.CalendarEntryKind
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.calendar_due
import ch.qscqlmpa.wingtracker.ui.resources.calendar_due_description
import ch.qscqlmpa.wingtracker.ui.resources.calendar_planned
import ch.qscqlmpa.wingtracker.ui.resources.check_type_check
import ch.qscqlmpa.wingtracker.ui.resources.check_type_full
import ch.qscqlmpa.wingtracker.ui.resources.check_type_other
import ch.qscqlmpa.wingtracker.ui.resources.check_type_partial
import ch.qscqlmpa.wingtracker.ui.resources.check_type_repack
import org.jetbrains.compose.resources.getString

/** Words a calendar entry in the user's language; outside composition, so it uses the suspending lookup. */
suspend fun CalendarEntry.toIcsEvent(): IcsEvent =
    when (kind) {
        CalendarEntryKind.DueDate -> {
            IcsEvent(
                uid,
                date,
                getString(Res.string.calendar_due, gearName),
                getString(Res.string.calendar_due_description, gearName),
            )
        }

        CalendarEntryKind.PlannedCheck -> {
            IcsEvent(
                uid = uid,
                date = date,
                summary = getString(Res.string.calendar_planned, gearName, checkType?.let { typeText(it) }.orEmpty()),
                description = listOfNotNull(company, notes).joinToString("\n").ifEmpty { null },
            )
        }
    }

private suspend fun typeText(type: CheckType): String =
    getString(
        when (type) {
            CheckType.Partial -> Res.string.check_type_partial
            CheckType.Full -> Res.string.check_type_full
            CheckType.Check -> Res.string.check_type_check
            CheckType.Repack -> Res.string.check_type_repack
            CheckType.Other -> Res.string.check_type_other
        },
    )
