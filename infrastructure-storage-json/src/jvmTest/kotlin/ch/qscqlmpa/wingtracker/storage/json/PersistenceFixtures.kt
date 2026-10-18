package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.SiteNames
import ch.qscqlmpa.wingtracker.flights.SiteSource
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
import ch.qscqlmpa.wingtracker.gear.wing.CertificationClass
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Own factories: persistence tests must not break when domain test helpers change.
fun aStoredWing(
    id: String = "3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47",
    name: String = "Sky Dancer",
    status: GearStatus = GearStatus.Active,
    interval: MaintenanceInterval? = null,
    checks: List<MaintenanceCheck> = emptyList(),
    plannedChecks: List<PlannedCheck> = emptyList(),
    inServiceDate: LocalDate? = LocalDate(2024, 3, 9),
) = Wing(
    id = WingId(id),
    details =
        WingDetails(
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
            inServiceDate = inServiceDate,
            hoursBeforeTracking = 42.hours + 30.minutes,
            flightsBeforeTracking = 61,
            maintenanceInterval = interval,
        ),
    status = status,
    checks = checks,
    plannedChecks = plannedChecks,
)

fun aStoredFlight(
    id: String = "9d41c6a2-5b7e-4f08-8c13-2a6e0f9b7d35",
    wingId: String = "3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47",
    notes: String? = "Thermic, smooth air",
    takeOffTime: LocalTime? = LocalTime(13, 5),
) = Flight(
    id = FlightId(id),
    details =
        FlightDetails(
            date = LocalDate(2026, 9, 20),
            duration = 95.minutes,
            wingId = WingId(wingId),
            takeOffSite = SiteNames.of(SiteSource.Manual, "Niederhorn"),
            landingSite = SiteNames.of(SiteSource.Manual, "Beatenbucht"),
            notes = notes,
            takeOffTime = takeOffTime,
        ),
    loggedAt = Instant.parse("2026-09-20T17:00:00Z"),
)

fun aStoredCheck(
    id: String = "check-1",
    type: CheckType = CheckType.Full,
    restartsInterval: Boolean = type != CheckType.Other,
) = MaintenanceCheck(
    id = CheckId(id),
    date = LocalDate(2025, 4, 10),
    type = type,
    company = "Swiss Paragliding Service",
    notes = "Lines within tolerance",
    restartsInterval = restartsInterval,
    reports = listOf(Attachment(AttachmentId("report-1"), "check-report-2025.pdf")),
)

fun aStoredPlannedCheck() =
    PlannedCheck(
        PlannedCheckId("plan-1"),
        LocalDate(2026, 12, 1),
        CheckType.Partial,
        "Swiss Paragliding Service",
        "Trim check",
    )

fun aStoredHarness(
    id: String = "harness-1",
    status: GearStatus = GearStatus.Active,
    checks: List<MaintenanceCheck> = emptyList(),
) = ch.qscqlmpa.wingtracker.gear.harness.Harness(
    id =
        ch.qscqlmpa.wingtracker.gear.harness
            .HarnessId(id),
    details =
        ch.qscqlmpa.wingtracker.gear.harness.HarnessDetails(
            name = "Cockpit",
            manufacturer = "Advance",
            model = "Impress 4",
            size = "M",
            serialNumber = "ADV-H-20491",
            acquisition =
                Acquisition(
                    AcquisitionCondition.New,
                    LocalDate(2024, 2, 20),
                    Price(89_000, CurrencyCode("CHF")),
                ),
            inServiceDate = LocalDate(2024, 3, 1),
            maintenanceInterval = MaintenanceInterval(months = 24),
        ),
    status = status,
    checks = checks,
)

fun aStoredReserve(
    id: String = "reserve-1",
    status: GearStatus = GearStatus.Active,
    checks: List<MaintenanceCheck> = emptyList(),
) = ch.qscqlmpa.wingtracker.gear.reserve.Reserve(
    id =
        ch.qscqlmpa.wingtracker.gear.reserve
            .ReserveId(id),
    details =
        ch.qscqlmpa.wingtracker.gear.reserve.ReserveDetails(
            name = "Rescue",
            manufacturer = "Gin",
            model = "Yeti Cross",
            size = "XL",
            serialNumber = "GIN-R-7731",
            manufactureDate = LocalDate(2023, 11, 5),
            acquisition = Acquisition(AcquisitionCondition.SecondHand, LocalDate(2024, 2, 20)),
            inServiceDate = LocalDate(2024, 3, 1),
            maintenanceInterval = MaintenanceInterval(months = 12),
        ),
    status = status,
    checks = checks,
)

/**
 * Rewrites a stored file as a newer version of the application would: a higher schema version and a
 * field this version does not know, which is what newer files hold in practice.
 */
fun Path.rewriteAsNewerVersion() =
    writeText(
        readText().replace(
            Regex("\"schemaVersion\": \\d+"),
            // Whatever the current version is, 999 is newer.
            "\"schemaVersion\": 999, \"addedByANewerVersion\": true",
        ),
    )
