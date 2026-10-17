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
    // Services the flight was sent to, so exporting again never sends it twice (ADR 0029).
    val exports: List<FlightExport> = emptyList(),
) {
    init {
        require(exports.distinctBy { it.system }.size == exports.size) { "A flight is exported once per system" }
    }

    fun isExportedTo(system: String): Boolean = exports.any { it.system == system }

    /** [externalId] is the flight's id in [system], when it could be found out, so it can be deleted there later. */
    fun markExported(
        system: String,
        at: Instant,
        externalId: String? = null,
    ): Flight {
        check(!isExportedTo(system)) { "Flight ${id.value} was already exported to $system" }
        return copy(exports = exports + FlightExport(system, at, externalId))
    }

    fun exportTo(system: String): FlightExport? = exports.firstOrNull { it.system == system }

    /** Drops the record of the export to [system], e.g. after deleting the flight there, so it can be sent again. */
    fun forgetExport(system: String): Flight = copy(exports = exports.filterNot { it.system == system })

    /** Puts a forgotten record back, unless the flight was exported to that system again meanwhile. */
    fun restoreExport(export: FlightExport): Flight =
        if (isExportedTo(export.system)) this else copy(exports = exports + export)

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

/** Records that a flight was sent to [system], e.g. "airtime", when, and its id there when it is known. */
data class FlightExport(
    val system: String,
    val at: Instant,
    val externalId: String? = null,
) {
    init {
        require(system.isNotBlank()) { "An export needs a system" }
        require(externalId == null || externalId.isNotBlank()) { "An external id cannot be blank" }
    }
}
