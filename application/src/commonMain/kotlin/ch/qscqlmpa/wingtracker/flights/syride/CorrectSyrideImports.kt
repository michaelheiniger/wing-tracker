package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository

/** A text an earlier import stored wrongly, and what Syride actually says. */
data class TextCorrection(
    val stored: String,
    val corrected: String,
)

/** An imported flight whose notes or take-off site the earlier reader garbled; null where that text is fine. */
data class SyrideCorrection(
    val flightId: FlightId,
    val notes: TextCorrection?,
    val takeOffSite: TextCorrection?,
)

/**
 * What to correct on [stored], imported from [fetched] by the earlier reader. A text is only corrected while it is
 * still exactly what that reader made of it, so whatever the pilot wrote is kept.
 */
internal fun correctionOf(
    stored: Flight,
    fetched: SyrideFlight,
): SyrideCorrection? {
    val earlier = fetched.earlierReading ?: return null
    val notes = correction(stored.details.notes, earlier.name, fetched.name)
    val site = correction(stored.details.takeOffSite, earlier.siteName, fetched.siteName)
    return if (notes == null && site == null) null else SyrideCorrection(stored.id, notes, site)
}

private fun correction(
    stored: String?,
    earlier: String?,
    actual: String?,
): TextCorrection? {
    // Anything but the earlier reader's exact text is the pilot's own.
    if (stored == null || stored != earlier) return null
    return actual?.takeIf { it != stored }?.let { TextCorrection(stored, it) }
}

/** Corrects flights an earlier import stored with garbled names or sites, once the pilot asked for it. */
class CorrectSyrideImports(
    private val flights: FlightRepository,
) {
    /** Returns how many flights were corrected. */
    suspend operator fun invoke(corrections: List<SyrideCorrection>): Int =
        corrections.count { correction ->
            val flight = flights.findById(correction.flightId) ?: return@count false
            val details = flight.details
            // A text the pilot changed since the account was read is theirs now, and stays.
            val notes = correction.notes?.takeIf { it.stored == details.notes }?.corrected ?: details.notes
            val site =
                correction.takeOffSite?.takeIf { it.stored == details.takeOffSite }?.corrected ?: details.takeOffSite
            if (notes == details.notes && site == details.takeOffSite) return@count false
            flights.save(flight.withDetails(details.copy(notes = notes, takeOffSite = site)))
            true
        }
}
