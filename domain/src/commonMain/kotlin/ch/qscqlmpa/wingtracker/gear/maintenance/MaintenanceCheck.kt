package ch.qscqlmpa.wingtracker.gear.maintenance

import ch.qscqlmpa.wingtracker.attachments.Attachment
import kotlinx.datetime.LocalDate

/** A check that was performed. [reports] hold the files (e.g. PDFs) handed over by the workshop. */
data class MaintenanceCheck(
    val id: CheckId,
    val date: LocalDate,
    val type: CheckType,
    val company: String? = null,
    val notes: String? = null,
    // Partial and full checks always restart the interval; for Other the pilot decides per entry.
    val restartsInterval: Boolean = type != CheckType.Other,
    val reports: List<Attachment> = emptyList(),
) {
    init {
        require(type == CheckType.Other || restartsInterval) {
            "A partial or full check always restarts the interval"
        }
    }
}

/** A check scheduled for later; completing it turns it into a [MaintenanceCheck]. */
data class PlannedCheck(
    val id: PlannedCheckId,
    val date: LocalDate,
    val type: CheckType,
    val company: String? = null,
    val notes: String? = null,
)
