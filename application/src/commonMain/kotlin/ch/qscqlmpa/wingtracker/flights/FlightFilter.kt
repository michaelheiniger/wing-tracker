package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.shared.folded
import kotlinx.datetime.LocalDate

/**
 * What the pilot looks for in the logbook. Every criterion that is set must match; an empty filter matches every
 * flight. [text] is split into words and each word must appear in the site names, notes, instrument or wing name.
 */
data class FlightFilter(
    val text: String = "",
    // Empty means any wing.
    val wingIds: Set<WingId> = emptySet(),
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    // Empty means any type; a flight of unknown type only matches then.
    val types: Set<FlightType> = emptySet(),
) {
    val isActive: Boolean
        get() = words.isNotEmpty() || wingIds.isNotEmpty() || from != null || to != null || types.isNotEmpty()

    private val words: List<String> = text.folded().split(WHITESPACE).filter { it.isNotEmpty() }

    // The wing name is passed in because it belongs to another aggregate than the flight.
    fun matches(
        flight: Flight,
        wingName: String?,
    ): Boolean {
        val details = flight.details
        if (wingIds.isNotEmpty() && details.wingId !in wingIds) return false
        if (from != null && details.date < from) return false
        if (to != null && details.date > to) return false
        if (types.isNotEmpty() && details.type !in types) return false
        if (words.isEmpty()) return true
        val searchable =
            // Every source's name, so a flight is found by the Syride name also after the pilot renamed the site.
            (
                details.takeOffSite.bySource.values +
                    details.landingSite.bySource.values +
                    listOfNotNull(details.notes, details.instrument, wingName)
            ).joinToString(" ")
                .folded()
        return words.all { it in searchable }
    }

    private companion object {
        val WHITESPACE = Regex("""\s+""")
    }
}
