package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.CurrencyCode
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.Price
import ch.qscqlmpa.wingtracker.gear.wing.CertificationClass
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Own factories: persistence tests must not break when domain test helpers change.
fun aStoredWing(
    id: String = "3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47",
    name: String = "Sky Dancer",
    status: GearStatus = GearStatus.Active,
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
            inServiceDate = LocalDate(2024, 3, 9),
            hoursBeforeTracking = 42.hours + 30.minutes,
        ),
    status = status,
)

fun aStoredFlight(
    id: String = "9d41c6a2-5b7e-4f08-8c13-2a6e0f9b7d35",
    wingId: String = "3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47",
    notes: String? = "Thermic, smooth air",
) = Flight(
    id = FlightId(id),
    details =
        FlightDetails(
            date = LocalDate(2026, 9, 20),
            duration = 95.minutes,
            wingId = WingId(wingId),
            takeOffSite = "Niederhorn",
            landingSite = "Beatenbucht",
            notes = notes,
        ),
    loggedAt = Instant.parse("2026-09-20T17:00:00Z"),
)
