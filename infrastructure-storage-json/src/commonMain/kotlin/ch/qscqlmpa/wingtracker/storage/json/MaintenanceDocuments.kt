package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheckId
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
internal data class AttachmentDocument(
    val id: String,
    val fileName: String,
)

/** Stored inside the wing's document: the history is part of the aggregate (ADR 0013). */
@Serializable
internal data class CheckDocument(
    val id: String,
    val date: LocalDate,
    val type: String,
    val company: String? = null,
    val notes: String? = null,
    val restartsInterval: Boolean,
    val reports: List<AttachmentDocument> = emptyList(),
) {
    fun toDomain() =
        MaintenanceCheck(
            id = CheckId(id),
            date = date,
            type = CheckType.valueOf(type),
            company = company,
            notes = notes,
            restartsInterval = restartsInterval,
            reports = reports.map { Attachment(AttachmentId(it.id), it.fileName) },
        )

    companion object {
        fun from(check: MaintenanceCheck) =
            CheckDocument(
                id = check.id.value,
                date = check.date,
                type = check.type.name,
                company = check.company,
                notes = check.notes,
                restartsInterval = check.restartsInterval,
                reports = check.reports.map { AttachmentDocument(it.id.value, it.fileName) },
            )
    }
}

@Serializable
internal data class PlannedCheckDocument(
    val id: String,
    val date: LocalDate,
    val type: String,
    val company: String? = null,
    val notes: String? = null,
) {
    fun toDomain() = PlannedCheck(PlannedCheckId(id), date, CheckType.valueOf(type), company, notes)

    companion object {
        fun from(planned: PlannedCheck) =
            PlannedCheckDocument(planned.id.value, planned.date, planned.type.name, planned.company, planned.notes)
    }
}
