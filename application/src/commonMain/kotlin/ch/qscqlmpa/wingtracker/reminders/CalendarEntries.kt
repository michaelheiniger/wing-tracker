package ch.qscqlmpa.wingtracker.reminders

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGearRepository
import ch.qscqlmpa.wingtracker.gear.maintenance.maintenanceStatus
import ch.qscqlmpa.wingtracker.settings.LogbookSettingsRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

enum class CalendarEntryKind { PlannedCheck, DueDate }

/** An all-day calendar event; the UI layer words it in the user's language. */
data class CalendarEntry(
    // Stable across exports, so importing again updates an event instead of duplicating it.
    val uid: String,
    val date: LocalDate,
    val kind: CalendarEntryKind,
    val gearKind: GearKind,
    val gearName: String,
    val checkType: CheckType?,
    val company: String?,
    val notes: String?,
)

data class CalendarEntries(
    val entries: List<CalendarEntry>,
    // The calendar's own reminder opens this many days ahead, the same lead time the app uses.
    val leadDays: Int,
    val generatedAt: Instant,
)

/**
 * Planned checks plus the date-based due dates still ahead, for gear in service. Limits counted in flight hours
 * have no date, so they cannot appear in a calendar.
 */
class GetCalendarEntries(
    private val gear: MaintainedGearRepository,
    private val settings: LogbookSettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(): CalendarEntries {
        val today = clock.today()
        val entries = mutableListOf<CalendarEntry>()
        gear.findAll().filter { it.isActive }.forEach { item ->
            item.history.plannedChecks.filter { it.date >= today }.forEach { planned ->
                entries +=
                    CalendarEntry(
                        uid = "planned-${planned.id.value}@wing-tracker",
                        date = planned.date,
                        kind = CalendarEntryKind.PlannedCheck,
                        gearKind = item.ref.kind,
                        gearName = item.displayName,
                        checkType = planned.type,
                        company = planned.company,
                        notes = planned.notes,
                    )
            }
            val dueDate = item.maintenanceStatus(emptyList(), today)?.dueDate
            if (dueDate != null && dueDate >= today) {
                entries +=
                    CalendarEntry(
                        uid = "due-${item.ref.kind.name.lowercase()}-${item.ref.id}@wing-tracker",
                        date = dueDate,
                        kind = CalendarEntryKind.DueDate,
                        gearKind = item.ref.kind,
                        gearName = item.displayName,
                        checkType = null,
                        company = null,
                        notes = null,
                    )
            }
        }
        return CalendarEntries(
            entries = entries.sortedWith(compareBy<CalendarEntry> { it.date }.thenBy { it.gearName.lowercase() }),
            leadDays = settings.load().reminderLeadTimes.days,
            generatedAt = clock.now(),
        )
    }
}
