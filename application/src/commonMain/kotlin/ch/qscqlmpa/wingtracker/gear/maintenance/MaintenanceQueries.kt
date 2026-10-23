package ch.qscqlmpa.wingtracker.gear.maintenance

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.settings.LogbookSettingsRepository
import kotlin.time.Duration

data class GearMaintenance(
    val gear: MaintainedGear,
    val status: MaintenanceStatus?,
    // Everything on a wing's clock: hours before tracking plus every logged flight. Null for gear without flights.
    val totalHours: Duration?,
    // Offered as suggestions when entering a company, most used first.
    val knownCompanies: List<String>,
)

/** Hours flown on this gear, dated; empty for gear that is not linked to flights. */
fun List<Flight>.flownOn(gear: MaintainedGear): List<FlownHours> =
    if (!gear.ref.kind.countsFlightHours) {
        emptyList()
    } else {
        filter { it.details.wingId == WingId(gear.ref.id) }.map { FlownHours(it.details.date, it.details.duration) }
    }

class GetGearMaintenance(
    private val gear: MaintainedGearRepository,
    private val flights: FlightRepository,
    private val settings: LogbookSettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(ref: GearRef): GearMaintenance {
        val item = gear.require(ref)
        val flown = flights.findAll().items.flownOn(item)
        val total =
            (item as? Wing)?.let { wing ->
                flown.fold(wing.details.hoursBeforeTracking) { sum, f ->
                    sum +
                        f.duration
                }
            }
        val status = item.maintenanceStatus(flown, clock.today(), settings.load().reminderLeadTimes)
        return GearMaintenance(item, status, total, companiesUsed(gear.findAll()))
    }

    private fun companiesUsed(all: List<MaintainedGear>): List<String> =
        all
            .flatMap {
                it.history.checks.mapNotNull { check -> check.company } +
                    it.history.plannedChecks.mapNotNull { p -> p.company }
            }.groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .map { it.key }
}

/** One status per piece of gear for the lists, computed from all flights in a single read. */
class GetMaintenanceStatuses(
    private val gear: MaintainedGearRepository,
    private val flights: FlightRepository,
    private val settings: LogbookSettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(): Map<GearRef, MaintenanceStatus?> {
        val allFlights = flights.findAll().items
        val leadTimes = settings.load().reminderLeadTimes
        return gear.findAll().associate { item ->
            item.ref to item.maintenanceStatus(allFlights.flownOn(item), clock.today(), leadTimes)
        }
    }
}
