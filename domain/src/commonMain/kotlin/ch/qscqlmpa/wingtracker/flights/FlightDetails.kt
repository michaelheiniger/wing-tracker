package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Duration

/** What a pilot enters about a flight. Flights are linked to a wing only, never to a harness or reserve. */
data class FlightDetails(
    val date: LocalDate,
    val duration: Duration,
    val wingId: WingId,
    val takeOffSite: String? = null,
    val landingSite: String? = null,
    val notes: String? = null,
    // Local time; it orders the flights of a day and ties a track to its flight (ADR 0026).
    val takeOffTime: LocalTime? = null,
    // Known when the flight comes from a tracking service; not entered by hand.
    val distanceKm: Double? = null,
    val topAltitudeMeters: Int? = null,
    val instrument: String? = null,
) {
    init {
        require(duration.isPositive()) { "A flight must last longer than zero" }
        require(distanceKm == null || distanceKm >= 0) { "A distance cannot be negative" }
        require(topAltitudeMeters == null || topAltitudeMeters >= 0) { "An altitude cannot be negative" }
    }
}
