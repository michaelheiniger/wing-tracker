package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.CurrencyCode
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.Price
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

// Deliberately separate from the domain fixtures so the modules' tests do not depend on each other.
fun aWingDetails(
    name: String = "Sky Dancer",
    hoursBeforeTracking: Duration = 42.hours,
    maintenanceInterval: MaintenanceInterval? = null,
) = WingDetails(
    name = name,
    manufacturer = "Advance",
    model = "Iota 3",
    size = "M",
    serialNumber = "ADV-2023-04817",
    certificationClass = CertificationClass.B,
    acquisition =
        Acquisition(
            condition = AcquisitionCondition.SecondHand,
            purchaseDate = LocalDate(2024, 3, 2),
            price = Price(minorUnits = 215_000, currency = CurrencyCode("CHF")),
        ),
    inServiceDate = LocalDate(2024, 3, 9),
    hoursBeforeTracking = hoursBeforeTracking,
    maintenanceInterval = maintenanceInterval,
)

fun aWing(
    id: String = "3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47",
    details: WingDetails = aWingDetails(),
    status: GearStatus = GearStatus.Active,
    checks: List<MaintenanceCheck> = emptyList(),
    plannedChecks: List<PlannedCheck> = emptyList(),
) = Wing(id = WingId(id), details = details, status = status, checks = checks, plannedChecks = plannedChecks)
