package ch.qscqlmpa.wingtracker.ui.gear.maintenance

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckDetails
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheckDetails
import kotlinx.datetime.LocalDate

/** Form for a performed check. Files already stored are kept or removed; new ones are added. */
data class CheckFormInput(
    val date: LocalDate? = null,
    val type: CheckType = CheckType.Full,
    val company: String = "",
    val notes: String = "",
    val restartsInterval: Boolean = true,
    val keptReports: List<Attachment> = emptyList(),
    val newReports: List<NewAttachment> = emptyList(),
    // Remembered so that removed files can be told apart from files that were never there.
    val originalReports: List<Attachment> = emptyList(),
) {
    fun withType(newType: CheckType): CheckFormInput =
        // Partial and full checks always restart the interval, so the choice only exists for Other.
        copy(type = newType, restartsInterval = newType != CheckType.Other || restartsInterval)

    companion object {
        fun from(check: MaintenanceCheck) =
            CheckFormInput(
                date = check.date,
                type = check.type,
                company = check.company.orEmpty(),
                notes = check.notes.orEmpty(),
                restartsInterval = check.restartsInterval,
                keptReports = check.reports,
                originalReports = check.reports,
            )

        /** Completing a plan starts from what was planned; the date is the day it really happened. */
        fun completing(
            planned: PlannedCheck,
            today: LocalDate,
        ) = CheckFormInput(
            date = today,
            type = planned.type,
            company = planned.company.orEmpty(),
            notes = planned.notes.orEmpty(),
            restartsInterval = planned.type != CheckType.Other,
        )
    }
}

enum class CheckFormField { Date }

enum class CheckFormError { Required, DateInFuture }

/** Valid input also carries which stored files to add and remove, because saving is one operation. */
sealed interface CheckFormResult {
    data class Valid(
        val details: CheckDetails,
        val newFiles: List<NewAttachment>,
        val removedFiles: Set<AttachmentId>,
    ) : CheckFormResult

    data class Invalid(
        val errors: Map<CheckFormField, CheckFormError>,
    ) : CheckFormResult
}

fun CheckFormInput.validate(): CheckFormResult {
    val checkDate = date ?: return CheckFormResult.Invalid(mapOf(CheckFormField.Date to CheckFormError.Required))
    return CheckFormResult.Valid(
        details =
            CheckDetails(
                date = checkDate,
                type = type,
                company = company.ifBlank { null },
                notes = notes.ifBlank { null },
                restartsInterval = restartsInterval,
            ),
        newFiles = newReports,
        removedFiles = originalReports.map { it.id }.toSet() - keptReports.map { it.id }.toSet(),
    )
}

data class PlanFormInput(
    val date: LocalDate? = null,
    val type: CheckType = CheckType.Full,
    val company: String = "",
    val notes: String = "",
) {
    companion object {
        fun from(planned: PlannedCheck) =
            PlanFormInput(planned.date, planned.type, planned.company.orEmpty(), planned.notes.orEmpty())
    }
}

enum class PlanFormError { Required, DateInPast }

sealed interface PlanFormResult {
    data class Valid(
        val details: PlannedCheckDetails,
    ) : PlanFormResult

    data class Invalid(
        val errors: Map<CheckFormField, PlanFormError>,
    ) : PlanFormResult
}

fun PlanFormInput.validate(): PlanFormResult {
    val plannedDate = date ?: return PlanFormResult.Invalid(mapOf(CheckFormField.Date to PlanFormError.Required))
    return PlanFormResult.Valid(
        PlannedCheckDetails(plannedDate, type, company.ifBlank { null }, notes.ifBlank { null }),
    )
}
