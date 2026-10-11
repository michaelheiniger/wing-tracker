package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.CurrencyCode
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.Price
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheckId
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/** Fully populated, valid domain objects; tests tweak the one attribute they are about. */
fun aWingDetails(
    name: String = "Sky Dancer",
    manufacturer: String = "Advance",
    model: String = "Iota 3",
    size: String? = "M",
    serialNumber: String? = "ADV-2023-04817",
    certificationClass: CertificationClass? = CertificationClass.B,
    acquisition: Acquisition = aNewAcquisition(),
    inServiceDate: LocalDate = LocalDate(2023, 5, 1),
    hoursBeforeTracking: Duration = Duration.ZERO,
    maintenanceInterval: MaintenanceInterval? = null,
) = WingDetails(
    name = name,
    manufacturer = manufacturer,
    model = model,
    size = size,
    serialNumber = serialNumber,
    certificationClass = certificationClass,
    acquisition = acquisition,
    inServiceDate = inServiceDate,
    hoursBeforeTracking = hoursBeforeTracking,
    maintenanceInterval = maintenanceInterval,
)

fun aNewAcquisition() =
    Acquisition(
        condition = AcquisitionCondition.New,
        purchaseDate = LocalDate(2023, 4, 15),
        price = Price(minorUnits = 349_000, currency = CurrencyCode("CHF")),
    )

fun aWing(
    id: WingId = WingId("3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47"),
    details: WingDetails = aWingDetails(),
    status: GearStatus = GearStatus.Active,
    checks: List<MaintenanceCheck> = emptyList(),
    plannedChecks: List<PlannedCheck> = emptyList(),
) = Wing(id = id, details = details, status = status, checks = checks, plannedChecks = plannedChecks)

fun aCheck(
    id: String = "check-1",
    date: LocalDate = LocalDate(2025, 4, 10),
    type: CheckType = CheckType.Full,
    restartsInterval: Boolean = type != CheckType.Other,
) = MaintenanceCheck(
    id = CheckId(id),
    date = date,
    type = type,
    company = "Swiss Paragliding Service",
    notes = "Lines within tolerance",
    restartsInterval = restartsInterval,
    reports = listOf(Attachment(AttachmentId("report-1"), "check-report-2025.pdf")),
)

fun aPlannedCheck(
    id: String = "plan-1",
    date: LocalDate = LocalDate(2026, 12, 1),
    type: CheckType = CheckType.Partial,
) = PlannedCheck(PlannedCheckId(id), date, type, company = "Swiss Paragliding Service")
