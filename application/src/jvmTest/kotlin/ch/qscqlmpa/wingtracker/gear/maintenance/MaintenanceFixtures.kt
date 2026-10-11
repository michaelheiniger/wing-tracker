package ch.qscqlmpa.wingtracker.gear.maintenance

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import kotlinx.datetime.LocalDate

// Own factories for the maintenance use cases; "today" is 2026-10-11 (see aFixedClock in the flights tests).
fun aCheckDetails(
    date: LocalDate = LocalDate(2026, 4, 10),
    type: CheckType = CheckType.Full,
) = CheckDetails(date = date, type = type, company = "Swiss Paragliding Service", notes = "Lines within tolerance")

fun aStoredCheck(
    id: String = "check-1",
    date: LocalDate = LocalDate(2025, 4, 10),
    reports: List<Attachment> = listOf(Attachment(AttachmentId("report-1"), "check-report-2025.pdf")),
) = MaintenanceCheck(
    id = CheckId(id),
    date = date,
    type = CheckType.Full,
    company = "Swiss Paragliding Service",
    notes = "Lines within tolerance",
    reports = reports,
)

fun aStoredPlannedCheck(
    id: String = "plan-1",
    date: LocalDate = LocalDate(2026, 12, 1),
) = PlannedCheck(PlannedCheckId(id), date, CheckType.Partial, company = "Swiss Paragliding Service")

fun aPdf(name: String = "check-report-2026.pdf") = NewAttachment(name, byteArrayOf(37, 80, 68, 70))

fun aHarnessWithInterval() =
    ch.qscqlmpa.wingtracker.gear.harness.Harness(
        id =
            ch.qscqlmpa.wingtracker.gear.harness
                .HarnessId("harness-1"),
        details =
            ch.qscqlmpa.wingtracker.gear.harness.HarnessDetails(
                name = "Cockpit",
                manufacturer = "Advance",
                model = "Impress 4",
                acquisition =
                    ch.qscqlmpa.wingtracker.gear.Acquisition(
                        ch.qscqlmpa.wingtracker.gear.AcquisitionCondition.New,
                    ),
                inServiceDate = LocalDate(2025, 3, 1),
                maintenanceInterval = MaintenanceInterval(months = 12),
            ),
    )
