package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.SiteSource

/** Where the take-off site Syride gave a flight can be seen there. */
class GetSyrideSitePage(
    private val source: SyrideFlightSource,
) {
    /** Null when Syride gave no site with a page, e.g. for a flight imported before its id was kept. */
    operator fun invoke(flight: Flight): String? =
        flight.details.takeOffSite
            .reference(SiteSource.Syride)
            ?.let(source::sitePage)
}
