package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** A flight imported from Syride earlier whose take-off time is missing or differs from Syride's start time. */
data class TakeOffTimeUpdate(
    val flightId: FlightId,
    val date: LocalDate,
    val stored: LocalTime?,
    val fromSyride: LocalTime,
)

/**
 * Gives flights imported earlier Syride's start time as take-off time, once the pilot agreed, so their tracks can be
 * linked to them (ADR 0026).
 */
class UpdateSyrideTakeOffTimes(
    private val flights: FlightRepository,
) {
    /** Returns how many flights were updated. */
    suspend operator fun invoke(updates: List<TakeOffTimeUpdate>): Int =
        updates.count { update ->
            val flight = flights.findById(update.flightId) ?: return@count false
            // The pilot agreed to replace the time they were shown; one changed since then is left alone.
            if (flight.details.takeOffTime != update.stored) return@count false
            flights.save(flight.withDetails(flight.details.copy(takeOffTime = update.fromSyride)))
            true
        }
}
