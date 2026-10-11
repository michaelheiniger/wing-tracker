package ch.qscqlmpa.wingtracker.flights

import kotlin.time.Instant

/** [loggedAt] orders flights of the same day by when they were entered, since only the date is recorded. */
data class Flight(
    val id: FlightId,
    val details: FlightDetails,
    val loggedAt: Instant,
    // Set when the flight was imported from a track; editing the details never touches it.
    val igcFile: IgcFile? = null,
) {
    fun withDetails(details: FlightDetails): Flight = copy(details = details)
}
