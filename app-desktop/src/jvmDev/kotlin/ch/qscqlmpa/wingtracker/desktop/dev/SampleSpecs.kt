package ch.qscqlmpa.wingtracker.desktop.dev

import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.wing.CertificationClass

// All dates are relative to "today", so whenever the data is generated the reminders show every state.

/** A check that happened [monthsAgo] months (plus [daysLater] days) before today. */
data class CheckSpec(
    val monthsAgo: Int,
    val daysLater: Int = 0,
    val type: CheckType,
    val company: String = SWISS_SERVICE,
    val notes: String? = null,
    // An "other" check that does not restart the interval, e.g. a repair.
    val restartsInterval: Boolean = type != CheckType.Other,
    val withReport: Boolean = true,
)

data class WingSpec(
    val name: String,
    val manufacturer: String,
    val model: String,
    val size: String,
    val certification: CertificationClass,
    val serialNumber: String,
    val condition: AcquisitionCondition,
    val priceChf: Long,
    val inServiceMonthsAgo: Int,
    val hoursBeforeTracking: Int,
    val flightsBeforeTracking: Int = 0,
    val interval: MaintenanceInterval,
    val retiredMonthsAgo: Int? = null,
    val checks: List<CheckSpec> = emptyList(),
    val plannedInDays: Int? = null,
)

data class SimpleGearSpec(
    val name: String,
    val manufacturer: String,
    val model: String,
    val size: String,
    val serialNumber: String,
    val priceChf: Long,
    val inServiceMonthsAgo: Int,
    val intervalMonths: Int,
    // Reserves only.
    val manufacturedMonthsAgo: Int? = null,
    val retiredMonthsAgo: Int? = null,
    val checks: List<CheckSpec> = emptyList(),
    val plannedInDays: Int? = null,
)

const val SWISS_SERVICE = "Swiss Paragliding Service"
const val ALPINE_SERVICE = "Alpine Wing Service"
private const val RESCUE_SERVICE = "Rescue Repack Centre"

/** The pilot's wings over four years: one retired, one overdue, one due soon, one fine, one brand new. */
val SAMPLE_WINGS =
    listOf(
        WingSpec(
            name = "Ion 7",
            manufacturer = "Advance",
            model = "Ion 7",
            size = "M",
            certification = CertificationClass.B,
            serialNumber = "ADV-ION7-04127",
            condition = AcquisitionCondition.New,
            priceChf = 387_000,
            inServiceMonthsAgo = 52,
            hoursBeforeTracking = 0,
            interval = MaintenanceInterval(months = 24),
            retiredMonthsAgo = 26,
            checks =
                listOf(
                    CheckSpec(
                        40,
                        type = CheckType.Full,
                        company = ALPINE_SERVICE,
                        notes = "Porosity and line strength within limits",
                    ),
                    CheckSpec(28, type = CheckType.Partial, notes = "Trim corrected on the B-lines"),
                ),
        ),
        WingSpec(
            name = "Iota 3",
            manufacturer = "Advance",
            model = "Iota 3",
            size = "M",
            certification = CertificationClass.B,
            serialNumber = "ADV-IOTA3-08811",
            condition = AcquisitionCondition.SecondHand,
            priceChf = 215_000,
            inServiceMonthsAgo = 30,
            hoursBeforeTracking = 42,
            flightsBeforeTracking = 61,
            interval = MaintenanceInterval(months = 12),
            checks =
                listOf(
                    CheckSpec(23, type = CheckType.Full, company = ALPINE_SERVICE, notes = "Lines within tolerance"),
                    CheckSpec(12, daysLater = 20, type = CheckType.Partial, notes = "Trim check, no changes needed"),
                    CheckSpec(
                        5,
                        type = CheckType.Other,
                        notes = "Replaced a worn A-line, wing otherwise unchanged",
                        withReport = false,
                    ),
                ),
        ),
        WingSpec(
            name = "Mantra 7",
            manufacturer = "Ozone",
            model = "Mantra 7",
            size = "M",
            certification = CertificationClass.D,
            serialNumber = "OZO-MAN7-20954",
            condition = AcquisitionCondition.SecondHand,
            priceChf = 168_000,
            inServiceMonthsAgo = 40,
            hoursBeforeTracking = 120,
            flightsBeforeTracking = 170,
            interval = MaintenanceInterval(months = 12),
            checks =
                listOf(
                    CheckSpec(27, type = CheckType.Full, company = ALPINE_SERVICE),
                    CheckSpec(14, daysLater = 10, type = CheckType.Partial),
                ),
        ),
        WingSpec(
            name = "Zeno 2",
            manufacturer = "Ozone",
            model = "Zeno 2",
            size = "S",
            certification = CertificationClass.C,
            serialNumber = "OZO-ZEN2-31177",
            condition = AcquisitionCondition.New,
            priceChf = 429_000,
            inServiceMonthsAgo = 14,
            hoursBeforeTracking = 0,
            interval = MaintenanceInterval(months = 12),
            checks =
                listOf(
                    CheckSpec(3, type = CheckType.Partial, notes = "First trim check after the break-in period"),
                ),
            plannedInDays = 18,
        ),
        WingSpec(
            name = "Rush 6",
            manufacturer = "Ozone",
            model = "Rush 6",
            size = "ML",
            certification = CertificationClass.B,
            serialNumber = "OZO-RUS6-40233",
            condition = AcquisitionCondition.New,
            priceChf = 398_000,
            inServiceMonthsAgo = 7,
            hoursBeforeTracking = 0,
            interval = MaintenanceInterval(months = 24),
        ),
    )

val SAMPLE_HARNESSES =
    listOf(
        SimpleGearSpec(
            name = "Cockpit",
            manufacturer = "Advance",
            model = "Impress 4",
            size = "M",
            serialNumber = "ADV-H-20491",
            priceChf = 89_000,
            inServiceMonthsAgo = 40,
            intervalMonths = 24,
            checks =
                listOf(
                    CheckSpec(
                        24,
                        daysLater = 25,
                        type = CheckType.Check,
                        company = ALPINE_SERVICE,
                        notes = "Protector and buckles fine",
                    ),
                ),
        ),
        SimpleGearSpec(
            name = "Range Air",
            manufacturer = "Skywalk",
            model = "Range Air",
            size = "M",
            serialNumber = "SKY-RA-3318",
            priceChf = 72_000,
            inServiceMonthsAgo = 84,
            intervalMonths = 24,
            retiredMonthsAgo = 40,
            checks = listOf(CheckSpec(55, type = CheckType.Check)),
        ),
    )

val SAMPLE_RESERVES =
    listOf(
        SimpleGearSpec(
            name = "Rescue",
            manufacturer = "Gin",
            model = "Yeti Cross",
            size = "XL",
            serialNumber = "GIN-R-7731",
            priceChf = 52_000,
            inServiceMonthsAgo = 30,
            intervalMonths = 12,
            manufacturedMonthsAgo = 40,
            checks =
                listOf(
                    CheckSpec(18, type = CheckType.Repack, company = RESCUE_SERVICE),
                    CheckSpec(
                        6,
                        type = CheckType.Repack,
                        company = RESCUE_SERVICE,
                        notes = "Aired and repacked, no damage",
                    ),
                ),
        ),
        SimpleGearSpec(
            name = "Backup",
            manufacturer = "High Adventure",
            model = "Beamer 3",
            size = "M",
            serialNumber = "HA-B3-5520",
            priceChf = 48_000,
            inServiceMonthsAgo = 28,
            intervalMonths = 12,
            manufacturedMonthsAgo = 36,
            checks = listOf(CheckSpec(13, type = CheckType.Repack, company = RESCUE_SERVICE)),
            plannedInDays = 5,
        ),
        SimpleGearSpec(
            name = "Old Beamer",
            manufacturer = "High Adventure",
            model = "Beamer 2",
            size = "M",
            serialNumber = "HA-B2-1190",
            priceChf = 41_000,
            inServiceMonthsAgo = 90,
            intervalMonths = 12,
            manufacturedMonthsAgo = 96,
            retiredMonthsAgo = 20,
            checks = listOf(CheckSpec(30, type = CheckType.Repack, company = RESCUE_SERVICE)),
        ),
    )

/** Take-off and landing sites that look like a real logbook. */
val SAMPLE_SITES =
    listOf(
        "Niederhorn" to "Beatenbucht",
        "Fronalpstock" to "Morschach",
        "Stanserhorn" to "Stans",
        "Brienzer Rothorn" to "Brienz",
        "Eggberge" to "Flüelen",
        "Hundwiler Höhi" to "Hundwil",
    )

val SAMPLE_NOTES =
    listOf(
        "Smooth thermals, base at 2400 m",
        "Soaring in the evening",
        "Strong wind at launch, short flight",
        "First flight of the season",
        "Cross-country attempt, landed early",
        "Ridge lift along the whole face",
    )
