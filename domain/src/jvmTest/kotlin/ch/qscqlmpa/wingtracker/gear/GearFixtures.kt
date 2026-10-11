package ch.qscqlmpa.wingtracker.gear

import ch.qscqlmpa.wingtracker.gear.harness.Harness
import ch.qscqlmpa.wingtracker.gear.harness.HarnessDetails
import ch.qscqlmpa.wingtracker.gear.harness.HarnessId
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.gear.reserve.Reserve
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveDetails
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveId
import kotlinx.datetime.LocalDate

// Factories for the gear other than wings; fully populated, tests tweak what they are about.
fun aHarnessDetails(
    name: String = "Cockpit",
    maintenanceInterval: MaintenanceInterval? = MaintenanceInterval(months = 24),
) = HarnessDetails(
    name = name,
    manufacturer = "Advance",
    model = "Impress 4",
    size = "M",
    serialNumber = "ADV-H-20491",
    acquisition = Acquisition(AcquisitionCondition.New, LocalDate(2024, 2, 20)),
    inServiceDate = LocalDate(2024, 3, 1),
    maintenanceInterval = maintenanceInterval,
)

fun aHarness(
    id: String = "harness-1",
    details: HarnessDetails = aHarnessDetails(),
    status: GearStatus = GearStatus.Active,
    checks: List<MaintenanceCheck> = emptyList(),
    plannedChecks: List<PlannedCheck> = emptyList(),
) = Harness(HarnessId(id), details, status, checks, plannedChecks)

fun aReserveDetails(
    name: String = "Rescue",
    manufactureDate: LocalDate? = LocalDate(2023, 11, 5),
    maintenanceInterval: MaintenanceInterval? = MaintenanceInterval(months = 12),
) = ReserveDetails(
    name = name,
    manufacturer = "Gin",
    model = "Yeti Cross",
    size = "XL",
    serialNumber = "GIN-R-7731",
    manufactureDate = manufactureDate,
    acquisition = Acquisition(AcquisitionCondition.New, LocalDate(2024, 2, 20)),
    inServiceDate = LocalDate(2024, 3, 1),
    maintenanceInterval = maintenanceInterval,
)

fun aReserve(
    id: String = "reserve-1",
    details: ReserveDetails = aReserveDetails(),
    status: GearStatus = GearStatus.Active,
    checks: List<MaintenanceCheck> = emptyList(),
    plannedChecks: List<PlannedCheck> = emptyList(),
) = Reserve(ReserveId(id), details, status, checks, plannedChecks)
