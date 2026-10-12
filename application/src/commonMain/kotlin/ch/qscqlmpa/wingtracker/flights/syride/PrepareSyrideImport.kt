package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/** The flights still to import on one Syride wing, with the existing wing that most likely is the same one. */
data class SyrideWingGroup(
    val wing: SyrideWing,
    val flights: List<SyrideFlight>,
    val suggestedWingId: WingId?,
) {
    val firstDate: LocalDate get() = flights.minOf { it.date }
    val lastDate: LocalDate get() = flights.maxOf { it.date }
    val totalDuration: Duration get() = flights.fold(Duration.ZERO) { sum, flight -> sum + flight.duration }
}

data class SyrideImportDraft(
    val username: String,
    val groups: List<SyrideWingGroup>,
    // Flights of the account that an earlier import already brought in.
    val alreadyImported: Int,
    val existingWings: List<Wing>,
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
                .mapNotNull { it.importedFrom }
                .filter { it.system == SYRIDE_SYSTEM }
                .map { it.id }
                .toSet()
        val (old, new) = fetched.partition { it.id in known }
        val existing = wings.findAll().items.sortedBy { it.details.name.lowercase() }
        val groups =
            new
                .groupBy { it.wing }
                .map { (wing, list) -> SyrideWingGroup(wing, list.sortedBy { it.date }, suggest(wing, existing)) }
                .sortedByDescending { it.lastDate }
        return SyrideImportDraft(username.trim(), groups, old.size, existing)
    }

    // The same name is a hint, not a fact: two wings can share a name, so the pilot still confirms the choice.
    private fun suggest(
        wing: SyrideWing,
        existing: List<Wing>,
    ): WingId? {
        val sameName = existing.filter { it.details.name.equals(wing.name, ignoreCase = true) }
        val sameBrand =
            sameName.filter {
                wing.brand != null &&
                    it.details.manufacturer.equals(wing.brand, ignoreCase = true)
            }
        return (sameBrand.firstOrNull() ?: sameName.firstOrNull { it.isActive } ?: sameName.firstOrNull())?.id
    }
}
