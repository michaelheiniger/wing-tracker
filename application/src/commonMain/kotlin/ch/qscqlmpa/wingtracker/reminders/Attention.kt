package ch.qscqlmpa.wingtracker.reminders

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.backup.BackupDue
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.maintenance.DueState
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGear
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGearRepository
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.flownOn
import ch.qscqlmpa.wingtracker.gear.maintenance.maintenanceStatus
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

/** One piece of gear that needs the pilot's attention: a limit reached or near, or a planned check coming up. */
data class AttentionItem(
    val ref: GearRef,
    val name: String,
    val state: DueState,
    val status: MaintenanceStatus?,
    val nextPlanned: PlannedCheck?,
) {
    val kind: GearKind get() = ref.kind
}

data class Attention(
    val items: List<AttentionItem>,
    val backup: BackupDue? = null,
) {
    // Gear only: the count is the Gear section's badge, and a backup is not gear.
    val count: Int get() = items.size
    val dueCount: Int get() = items.count { it.state == DueState.Due }
    val dueSoonCount: Int get() = items.count { it.state == DueState.DueSoon }
    val isEmpty: Boolean get() = items.isEmpty() && backup == null
}

/**
 * Computed on demand from the stored data (ADR 0012): nothing runs in the background, so what the pilot sees
 * is always the current state. Retired gear never asks for attention. A backup does when the last one grew older
 * than the pilot allows (ADR 0031).
 */
class GetAttention(
    private val gear: MaintainedGearRepository,
    private val flights: FlightRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(): Attention {
        val stored = settings.load()
        val leadTimes = stored.reminderLeadTimes
        val today = clock.today()
        val plannedHorizon = today.plus(leadTimes.days, DateTimeUnit.DAY)
        val allFlights = flights.findAll().items
        val allGear = gear.findAll()
        val items =
            allGear.filter { it.isActive }.mapNotNull { item ->
                val status = item.maintenanceStatus(allFlights.flownOn(item), today, leadTimes)
                val planned = item.history.nextPlannedCheck?.takeIf { it.date <= plannedHorizon }
                val state = status?.state ?: DueState.Ok
                if (state == DueState.Ok && planned == null) null else item.toAttention(state, status, planned)
            }
        val lastBackupOn = stored.lastBackupAt?.let { clock.local(it).date }
        val backup =
            BackupDue(lastBackupOn).takeIf {
                stored.backupReminder.isDue(
                    lastBackupOn,
                    today,
                    hasData =
                        allGear.isNotEmpty() || allFlights.isNotEmpty(),
                )
            }
        return Attention(
            items.sortedWith(compareBy<AttentionItem> { severity(it) }.thenBy { it.name.lowercase() }),
            backup,
        )
    }

    private fun MaintainedGear.toAttention(
        state: DueState,
        status: MaintenanceStatus?,
        planned: PlannedCheck?,
    ) = AttentionItem(ref, displayName, state, status, planned)

    private fun severity(item: AttentionItem) =
        when (item.state) {
            DueState.Due -> 0
            DueState.DueSoon -> 1
            DueState.Ok -> 2
        }
}
