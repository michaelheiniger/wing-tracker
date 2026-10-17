package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.settings.SettingsRepository

/**
 * Where a flight imported from Syride can be seen there. Flights do not remember the account they came from, so the
 * account saved for the import is used; a flight imported from another account would get a wrong address.
 */
class GetSyrideFlightPage(
    private val source: SyrideFlightSource,
    private val settings: SettingsRepository,
) {
    /** Null when the flight has no page on Syride, or no account is known yet. */
    suspend operator fun invoke(flight: Flight): String? {
        val number = flight.importedFrom?.syrideFlightNumber() ?: return null
        val username = settings.load().syrideUsername ?: return null
        return source.flightPage(username, number)
    }
}
