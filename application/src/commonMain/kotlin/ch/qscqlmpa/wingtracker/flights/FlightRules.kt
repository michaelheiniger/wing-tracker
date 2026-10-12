package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.gear.wing.WingNotFoundException
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository

/** Rules spanning flights and wings, so they live here rather than in either aggregate. */
internal class FlightRules(
    private val wings: WingRepository,
    private val clock: AppClock,
) {
    suspend fun ensureAllowed(details: FlightDetails) {
        // The wing must exist, but a flight dated before its in-service date is allowed: the date of a wing is
        // often entered later from memory, and the flights are the better record.
        wings.findById(details.wingId) ?: throw WingNotFoundException(details.wingId)
        if (details.date > clock.today()) throw FlightDateInFutureException()
    }
}
