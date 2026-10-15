package ch.qscqlmpa.wingtracker.flights.syride

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
    // Flights an earlier import stored with garbled names or sites, which the pilot can have corrected.
    val corrections: List<SyrideCorrection> = emptyList(),
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
        val corrections = old.mapNotNull { correctionOf(known.getValue(it.id), it) }
        val existing = wings.findAll().items.sortedBy { it.details.name.lowercase() }
        val groups =
            new
                .groupBy { it.wing }
                .map { (wing, list) -> WingGroup(wing, list.sortedBy { it.date }, suggestWing(wing, existing)) }
                .sortedByDescending { it.lastDate }
        return SyrideImportDraft(username.trim(), groups, old.size, existing, corrections)
    }
}
