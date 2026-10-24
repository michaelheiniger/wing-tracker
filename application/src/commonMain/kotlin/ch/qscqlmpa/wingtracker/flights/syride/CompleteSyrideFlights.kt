package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.FlightType
import ch.qscqlmpa.wingtracker.flights.SiteSource
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions

/**
 * What Syride now gives about a flight imported before it was read: its type, and the id of the take-off site that
 * goes with Syride's name for it. Each is null when there is nothing to fill.
 */
data class SyrideCompletion(
    val flightId: FlightId,
    val type: FlightType?,
    val siteName: String?,
    val siteId: String?,
)

/** What [fetched] can add to [stored] without changing anything already there; null when nothing. */
internal fun completionOf(
    stored: Flight,
    fetched: SyrideFlight,
): SyrideCompletion? {
    val type = fetched.type?.takeIf { stored.details.type == null }
    val site = stored.details.takeOffSite
    // A Syride name that differs from Syride's was most likely edited by the pilot before names had a source.
    val siteId =
        fetched.siteId?.takeIf {
            site.reference(SiteSource.Syride) == null && site[SiteSource.Syride] == fetched.siteName
        }
    if (type == null && siteId == null) return null
    return SyrideCompletion(stored.id, type, fetched.siteName?.takeIf { siteId != null }, siteId)
}

/** Fills in what Syride now gives on flights imported earlier, once the pilot agreed; nothing already set changes. */
class CompleteSyrideFlights(
    private val flights: FlightRepository,
    private val transactions: LogbookTransactions,
) {
    /** Returns how many flights were completed. */
    suspend operator fun invoke(completions: List<SyrideCompletion>): Int =
        transactions.run(LogbookChange.SyrideFlightsCompleted) { completions.count { complete(it) } }

    private suspend fun complete(completion: SyrideCompletion): Boolean {
        val flight = flights.findById(completion.flightId) ?: return false
        val details = flight.details
        // Checked again: the pilot may have set the type or edited the site since the import was read.
        val type = completion.type?.takeIf { details.type == null }
        val site = details.takeOffSite
        val siteId =
            completion.siteId?.takeIf {
                site.reference(SiteSource.Syride) == null && site[SiteSource.Syride] == completion.siteName
            }
        if (type == null && siteId == null) return false
        val completed =
            details.copy(
                type = type ?: details.type,
                takeOffSite =
                    if (siteId ==
                        null
                    ) {
                        site
                    } else {
                        site.with(SiteSource.Syride, completion.siteName, siteId)
                    },
            )
        flights.save(flight.withDetails(completed))
        return true
    }
}
