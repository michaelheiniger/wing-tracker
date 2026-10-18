package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.importing.WingGroup
import ch.qscqlmpa.wingtracker.flights.importing.suggestWing
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository

data class SyrideImportDraft(
    val username: String,
    val groups: List<WingGroup<SyrideFlight>>,
    // Flights of the account that an earlier import already brought in.
    val alreadyImported: Int,
    val existingWings: List<Wing>,
    // Flights imported earlier whose take-off time Syride gives differently; the pilot decides whether to update them.
    val takeOffTimeUpdates: List<TakeOffTimeUpdate> = emptyList(),
    // Flights imported earlier that can get what Syride gives since; the pilot decides whether to fill it in.
    val completions: List<SyrideCompletion> = emptyList(),
)

/** Reads the account and works out what is new, so the pilot only has to decide about the wings. */
class PrepareSyrideImport(
    private val source: SyrideFlightSource,
    private val flights: FlightRepository,
    private val wings: WingRepository,
) {
    suspend operator fun invoke(username: String): SyrideImportDraft {
        val fetched = source.fetch(username.trim())
        val known =
            flights
                .findAll()
                .items
                .filter { it.importedFrom?.system == SYRIDE_SYSTEM }
                .associateBy { checkNotNull(it.importedFrom).id }
        val (old, new) = fetched.partition { it.id in known }
        val timeUpdates = old.mapNotNull { takeOffTimeUpdate(known.getValue(it.id), it) }
        val completions = old.mapNotNull { completionOf(known.getValue(it.id), it) }
        val existing = wings.findAll().items.sortedBy { it.details.name.lowercase() }
        val groups =
            new
                .groupBy { it.wing }
                .map { (wing, list) -> WingGroup(wing, list.sortedBy { it.date }, suggestWing(wing, existing)) }
                .sortedByDescending { it.lastDate }
        return SyrideImportDraft(username.trim(), groups, old.size, existing, timeUpdates, completions)
    }
}

private fun takeOffTimeUpdate(
    stored: Flight,
    fetched: SyrideFlight,
): TakeOffTimeUpdate? {
    val fromSyride = fetched.startTime ?: return null
    if (fromSyride == stored.details.takeOffTime) return null
    return TakeOffTimeUpdate(stored.id, stored.details.date, stored.details.takeOffTime, fromSyride)
}
