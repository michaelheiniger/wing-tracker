package ch.qscqlmpa.wingtracker.flights.importing

import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId

/** Where the flights of one source wing go. */
sealed interface WingTarget {
    data class Existing(
        val wingId: WingId,
    ) : WingTarget

    // [key] names a wing to create, so several source wings can end up on the same new wing.
    data class New(
        val key: String,
    ) : WingTarget

    data object Skip : WingTarget
}

/** What a pilot says about a wing that does not exist yet; the rest is derived from the flights. */
data class NewWingSpec(
    val name: String,
    val manufacturer: String,
    val model: String,
)

// The same name is a hint, not a fact: two wings can share a name, so the pilot still confirms the choice.
internal fun suggestWing(
    wing: SourceWing,
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
