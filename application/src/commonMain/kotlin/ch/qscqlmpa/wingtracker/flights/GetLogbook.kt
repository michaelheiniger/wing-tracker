package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import kotlin.time.Duration

data class LogbookMonth(
    val month: YearMonth,
    val flights: List<Flight>,
) {
    val total: Duration get() = flights.fold(Duration.ZERO) { sum, flight -> sum + flight.details.duration }
}

data class Logbook(
    val months: List<LogbookMonth>,
    val wings: List<Wing>,
    // Derived, never stored: pre-tracking hours plus every logged flight of the wing.
    val hoursByWing: Map<WingId, Duration>,
    val unreadable: List<UnreadableRecord>,
) {
    val isEmpty: Boolean get() = months.isEmpty()

    val flightCount: Int get() = months.sumOf { it.flights.size }

    /** The flight types the logbook holds, so searching by type is only offered once there is something to find. */
    val types: Set<FlightType> get() = months.flatMap { it.flights }.mapNotNull { it.details.type }.toSet()

    val total: Duration get() = months.fold(Duration.ZERO) { sum, month -> sum + month.total }

    /**
     * Only the flights matching [filter], still grouped by month. [hoursByWing] stays the lifetime total, because it
     * tells the pilot about the wing (e.g. when its next check is due), not about the search.
     */
    fun filtered(filter: FlightFilter): Logbook {
        if (!filter.isActive) return this
        val names = wings.associate { it.id to it.details.name }
        return copy(
            months =
                months
                    .map { month ->
                        month.copy(flights = month.flights.filter { filter.matches(it, names[it.details.wingId]) })
                    }.filter { it.flights.isNotEmpty() },
        )
    }
}

class GetLogbook(
    private val flights: FlightRepository,
    private val wings: WingRepository,
) {
    suspend operator fun invoke(): Logbook {
        val loadedFlights = flights.findAll()
        val loadedWings = wings.findAll()

        val newestFirst =
            loadedFlights.items.sortedWith(
                compareByDescending<Flight> { it.details.date }
                    // Without a take-off time a flight cannot be placed among the timed ones, so it goes after them.
                    .thenByDescending { it.details.takeOffTime != null }
                    .thenByDescending { it.details.takeOffTime }
                    .thenByDescending { it.loggedAt },
            )
        val months =
            newestFirst
                .groupBy { it.details.date.yearMonth }
                .map { (month, monthFlights) -> LogbookMonth(month, monthFlights) }

        val flownByWing =
            loadedFlights.items
                .groupBy { it.details.wingId }
                .mapValues { (_, list) -> list.fold(Duration.ZERO) { sum, flight -> sum + flight.details.duration } }
        val hoursByWing =
            loadedWings.items.associate { wing ->
                wing.id to wing.details.hoursBeforeTracking + (flownByWing[wing.id] ?: Duration.ZERO)
            }

        return Logbook(
            months = months,
            wings = loadedWings.items.sortedBy { it.details.name.lowercase() },
            hoursByWing = hoursByWing,
            unreadable = loadedFlights.unreadable + loadedWings.unreadable,
        )
    }
}
