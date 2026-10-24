package ch.qscqlmpa.wingtracker.desktop.dev

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.CurrencyCode
import ch.qscqlmpa.wingtracker.gear.Price
import ch.qscqlmpa.wingtracker.gear.harness.HarnessDetails
import ch.qscqlmpa.wingtracker.gear.harness.HarnessRepository
import ch.qscqlmpa.wingtracker.gear.harness.RegisterHarness
import ch.qscqlmpa.wingtracker.gear.harness.RetireHarness
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckDetails
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.gear.maintenance.PlanCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheckDetails
import ch.qscqlmpa.wingtracker.gear.maintenance.RecordCheck
import ch.qscqlmpa.wingtracker.gear.reserve.RegisterReserve
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveDetails
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveRepository
import ch.qscqlmpa.wingtracker.gear.reserve.RetireReserve
import ch.qscqlmpa.wingtracker.gear.wing.RegisterWing
import ch.qscqlmpa.wingtracker.gear.wing.RetireWing
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.koin.core.Koin
import kotlin.time.Duration.Companion.hours

/** A wing together with the sample specification it was created from. */
private const val PURCHASE_DAYS_BEFORE_SERVICE = 5

class SeededWing(
    val wing: Wing,
    val spec: WingSpec,
    val retiredOn: LocalDate?,
)

/** Creates wings, harnesses and reserves through the real use cases, so the sample data follows the real rules. */
class GearSeeder(
    private val koin: Koin,
    private val today: LocalDate,
) {
    private val recordCheck = koin.get<RecordCheck>()
    private val planCheck = koin.get<PlanCheck>()

    suspend fun seedWings(): List<SeededWing> =
        SAMPLE_WINGS.map { spec ->
            val wing = koin.get<RegisterWing>()(wingDetails(spec))
            val retiredOn = spec.retiredMonthsAgo?.let { monthsAgo(it) }
            record(wing.ref, spec.checks, wing.details.name)
            spec.plannedInDays?.let { plan(wing.ref, it, ch.qscqlmpa.wingtracker.gear.maintenance.CheckType.Full) }
            // Retired last: the wing was in use, and checked, before it was sold.
            retiredOn?.let { koin.get<RetireWing>()(wing.id, it, "Sold") }
            SeededWing(wing, spec, retiredOn)
        }

    suspend fun seedHarnesses() {
        SAMPLE_HARNESSES.forEach { spec ->
            val harness = RegisterHarness(koin.get<HarnessRepository>(), koin.get())(harnessDetails(spec))
            record(harness.ref, spec.checks, spec.name)
            spec.retiredMonthsAgo?.let {
                RetireHarness(
                    koin.get<HarnessRepository>(),
                    koin.get(),
                )(harness.id, monthsAgo(it), "Replaced")
            }
        }
    }

    suspend fun seedReserves() {
        SAMPLE_RESERVES.forEach { spec ->
            val reserve = RegisterReserve(koin.get<ReserveRepository>(), koin.get())(reserveDetails(spec))
            record(reserve.ref, spec.checks, spec.name)
            spec.plannedInDays?.let { plan(reserve.ref, it, ch.qscqlmpa.wingtracker.gear.maintenance.CheckType.Repack) }
            spec.retiredMonthsAgo?.let {
                RetireReserve(
                    koin.get<ReserveRepository>(),
                    koin.get(),
                )(reserve.id, monthsAgo(it), "Replaced")
            }
        }
    }

    fun monthsAgo(months: Int): LocalDate = today.minus(months, DateTimeUnit.MONTH)

    private suspend fun record(
        ref: GearRef,
        checks: List<CheckSpec>,
        gearName: String,
    ) {
        checks.forEach { check ->
            val date = monthsAgo(check.monthsAgo).plus(check.daysLater, DateTimeUnit.DAY)
            val details = CheckDetails(date, check.type, check.company, check.notes, check.restartsInterval)
            val reports = if (check.withReport) listOf(report(gearName, check, date)) else emptyList()
            recordCheck(ref, details, reports)
        }
    }

    private suspend fun plan(
        ref: GearRef,
        inDays: Int,
        type: ch.qscqlmpa.wingtracker.gear.maintenance.CheckType,
    ) {
        planCheck(ref, PlannedCheckDetails(today.plus(inDays, DateTimeUnit.DAY), type, SWISS_SERVICE))
    }

    private fun report(
        gearName: String,
        check: CheckSpec,
        date: LocalDate,
    ) = NewAttachment(
        "check-report-$date.pdf",
        SampleFiles.pdf(
            "Inspection report",
            "Gear: $gearName",
            "Type: ${check.type}",
            "Date: $date",
            "Performed by: ${check.company}",
        ),
    )

    private fun acquisition(
        condition: ch.qscqlmpa.wingtracker.gear.AcquisitionCondition,
        priceChf: Long,
        inServiceMonthsAgo: Int,
    ) = Acquisition(
        condition,
        monthsAgo(inServiceMonthsAgo).minus(PURCHASE_DAYS_BEFORE_SERVICE, DateTimeUnit.DAY),
        Price(priceChf, CurrencyCode("CHF")),
    )

    private fun wingDetails(spec: WingSpec) =
        WingDetails(
            name = spec.name,
            manufacturer = spec.manufacturer,
            model = spec.model,
            size = spec.size,
            serialNumber = spec.serialNumber,
            certificationClass = spec.certification,
            acquisition = acquisition(spec.condition, spec.priceChf, spec.inServiceMonthsAgo),
            inServiceDate = monthsAgo(spec.inServiceMonthsAgo),
            hoursBeforeTracking = spec.hoursBeforeTracking.hours,
            flightsBeforeTracking = spec.flightsBeforeTracking,
            maintenanceInterval = spec.interval,
        )

    private fun harnessDetails(spec: SimpleGearSpec) =
        HarnessDetails(
            name = spec.name,
            manufacturer = spec.manufacturer,
            model = spec.model,
            size = spec.size,
            serialNumber = spec.serialNumber,
            acquisition =
                acquisition(
                    ch.qscqlmpa.wingtracker.gear.AcquisitionCondition.New,
                    spec.priceChf,
                    spec.inServiceMonthsAgo,
                ),
            inServiceDate = monthsAgo(spec.inServiceMonthsAgo),
            maintenanceInterval =
                ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval(
                    months = spec.intervalMonths,
                ),
        )

    private fun reserveDetails(spec: SimpleGearSpec) =
        ReserveDetails(
            name = spec.name,
            manufacturer = spec.manufacturer,
            model = spec.model,
            size = spec.size,
            serialNumber = spec.serialNumber,
            manufactureDate = spec.manufacturedMonthsAgo?.let { monthsAgo(it) },
            acquisition =
                acquisition(
                    ch.qscqlmpa.wingtracker.gear.AcquisitionCondition.New,
                    spec.priceChf,
                    spec.inServiceMonthsAgo,
                ),
            inServiceDate = monthsAgo(spec.inServiceMonthsAgo),
            maintenanceInterval =
                ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval(
                    months = spec.intervalMonths,
                ),
        )
}
