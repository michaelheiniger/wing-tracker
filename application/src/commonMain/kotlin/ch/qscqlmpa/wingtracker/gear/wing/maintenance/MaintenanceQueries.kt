package ch.qscqlmpa.wingtracker.gear.wing.maintenance

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import kotlin.time.Duration

data class WingMaintenance(
    val wing: Wing,
    val status: MaintenanceStatus?,
    // Everything on the clock: hours before tracking plus every logged flight.
    val totalHours: Duration,
    // Offered as suggestions when entering a company, most used first.
    val knownCompanies: List<String>,
)

private fun List<Flight>.flownOn(wingId: WingId): List<FlownHours> =
    filter { it.details.wingId == wingId }.map { FlownHours(it.details.date, it.details.duration) }

class GetWingMaintenance(
    private val wings: WingRepository,
    private val flights: FlightRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(wingId: WingId): WingMaintenance {
        val wing = wings.require(wingId)
        val flown = flights.findAll().items.flownOn(wingId)
        val total = flown.fold(wing.details.hoursBeforeTracking) { sum, f -> sum + f.duration }
        return WingMaintenance(
            wing,
            wing.maintenanceStatus(flown, clock.today()),
            total,
            companiesUsed(wings.findAll().items),
        )
    }

    private fun companiesUsed(all: List<Wing>): List<String> =
        all
            .flatMap { wing -> wing.checks.mapNotNull { it.company } + wing.plannedChecks.mapNotNull { it.company } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .map { it.key }
}

/** One status per wing for the wing list, computed from all flights in a single read. */
class GetMaintenanceStatuses(
    private val wings: WingRepository,
    private val flights: FlightRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(): Map<WingId, MaintenanceStatus?> {
        val allFlights = flights.findAll().items
        return wings.findAll().items.associate { wing ->
            wing.id to wing.maintenanceStatus(allFlights.flownOn(wing.id), clock.today())
        }
    }
}
