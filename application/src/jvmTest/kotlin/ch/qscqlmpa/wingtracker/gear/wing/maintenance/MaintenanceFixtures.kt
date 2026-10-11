package ch.qscqlmpa.wingtracker.gear.wing.maintenance

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import kotlinx.datetime.LocalDate

// Own factories for the maintenance use cases; "today" is 2026-10-11 (see aFixedClock in the flights tests).
fun aCheckDetails(
    date: LocalDate = LocalDate(2026, 4, 10),
    type: WingCheckType = WingCheckType.Full,
) = CheckDetails(date = date, type = type, company = "Swiss Paragliding Service", notes = "Lines within tolerance")

fun aStoredCheck(
    id: String = "check-1",
    date: LocalDate = LocalDate(2025, 4, 10),
    reports: List<Attachment> = listOf(Attachment(AttachmentId("report-1"), "check-report-2025.pdf")),
) = MaintenanceCheck(
    id = CheckId(id),
    date = date,
    type = WingCheckType.Full,
    company = "Swiss Paragliding Service",
    notes = "Lines within tolerance",
    reports = reports,
)

fun aStoredPlannedCheck(
    id: String = "plan-1",
    date: LocalDate = LocalDate(2026, 12, 1),
) = PlannedCheck(PlannedCheckId(id), date, WingCheckType.Partial, company = "Swiss Paragliding Service")

fun aPdf(name: String = "check-report-2026.pdf") = NewAttachment(name, byteArrayOf(37, 80, 68, 70))
