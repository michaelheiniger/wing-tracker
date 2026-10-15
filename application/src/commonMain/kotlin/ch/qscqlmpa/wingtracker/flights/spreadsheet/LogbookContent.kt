package ch.qscqlmpa.wingtracker.flights.spreadsheet

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/**
 * The flights already in the logbook, to recognise spreadsheet rows that were imported before: a spreadsheet has no
 * ids, so a row counts as present when a flight has the same date, wing, sites and duration. Notes are left out, as
 * the pilot may have edited them since.
 */
class LogbookContent(
    flights: List<Flight>,
) {
    private val counts: Map<Signature, Int> = flights.groupingBy { Signature.of(it.details) }.eachCount()

    /**
     * The flights of [planned] (each with the wing it would go on) that are already in the logbook. The same flight is
     * often flown several times a day, so identical ones are matched by count: two in the logbook and three in the
     * file leave one to import.
     */
    fun alreadyPresent(planned: List<Pair<SpreadsheetFlight, WingId>>): Set<SpreadsheetFlight> {
        val remaining = counts.toMutableMap()
        return planned
            .filter { (flight, wingId) ->
                val key = Signature.of(flight.toDetails(wingId))
                val left = remaining[key] ?: 0
                if (left > 0) remaining[key] = left - 1
                left > 0
            }.map { it.first }
            .toSet()
    }

    private data class Signature(
        val date: LocalDate,
        val wingId: WingId,
        val takeOffSite: String?,
        val landingSite: String?,
        val duration: Duration,
    ) {
        companion object {
            fun of(details: FlightDetails) =
                Signature(
                    details.date,
                    details.wingId,
                    details.takeOffSite.normalized(),
                    details.landingSite.normalized(),
                    details.duration,
                )

            // A site typed again by hand often differs only in case or spacing.
            private fun String?.normalized() = this?.trim()?.lowercase()?.ifEmpty { null }
        }
    }
}
