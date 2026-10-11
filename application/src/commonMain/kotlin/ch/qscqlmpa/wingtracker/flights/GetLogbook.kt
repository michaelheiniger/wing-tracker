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
                compareByDescending<Flight> { it.details.date }.thenByDescending { it.loggedAt },
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
