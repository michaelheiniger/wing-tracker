package ch.qscqlmpa.wingtracker.flights

import kotlin.time.Instant

/** [loggedAt] orders flights of the same day by when they were entered when their take-off time is not known. */
data class Flight(
    val id: FlightId,
    val details: FlightDetails,
    val loggedAt: Instant,
    // Set when the flight was imported from a track; editing the details never touches it.
    val igcFile: IgcFile? = null,
    // Set when the flight was imported from another service, so importing again never duplicates it.
    val importedFrom: ExternalReference? = null,
) {
    fun withDetails(details: FlightDetails): Flight = copy(details = details)

    /** Adds the track of a flight logged without one; a stored track is never replaced (ADR 0026). */
    fun attachTrack(track: IgcFile): Flight {
        check(igcFile == null) { "Flight ${id.value} already has a track" }
        return copy(igcFile = track)
    }
}

/** Identifies a flight in another system, e.g. [system] "syride" and the flight's id there. */
data class ExternalReference(
    val system: String,
    val id: String,
) {
    init {
        require(system.isNotBlank() && id.isNotBlank()) { "An external reference needs a system and an id" }
    }
}
