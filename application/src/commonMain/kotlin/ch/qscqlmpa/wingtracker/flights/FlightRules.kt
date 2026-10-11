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
        val wing = wingOf(details)
        if (details.date > clock.today()) throw FlightDateInFutureException()
        if (details.date < wing.details.inServiceDate) throw FlightBeforeWingInServiceException()
    }

    private suspend fun wingOf(details: FlightDetails) =
        wings.findById(details.wingId) ?: throw WingNotFoundException(details.wingId)
}
