package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.CurrencyCode
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.Price
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
) = Wing(id = id, details = details, status = status)
