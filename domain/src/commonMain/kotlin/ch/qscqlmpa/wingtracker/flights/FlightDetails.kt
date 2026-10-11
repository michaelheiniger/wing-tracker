package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/** What a pilot enters about a flight. Flights are linked to a wing only, never to a harness or reserve. */
data class FlightDetails(
    val date: LocalDate,
    val duration: Duration,
    val wingId: WingId,
    val takeOffSite: String? = null,
    val landingSite: String? = null,
    val notes: String? = null,
) {
    init {
        require(duration.isPositive()) { "A flight must last longer than zero" }
    }
}
