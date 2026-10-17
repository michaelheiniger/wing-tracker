package ch.qscqlmpa.wingtracker.ui.flights

import ch.qscqlmpa.wingtracker.flights.FlightFilter
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.ui.parseDate

/** The search as typed. Dates stay text so a half-typed day is kept in the field; it filters once it is a real day. */
data class LogbookSearch(
    val text: String = "",
    val wingIds: Set<WingId> = emptySet(),
    val from: String = "",
    val to: String = "",
) {
    val isActive: Boolean get() = toFilter().isActive

    // Unlike [isActive], also true for a half-typed date, which the pilot may still want to clear.
    val isBlank: Boolean get() = text.isBlank() && wingIds.isEmpty() && from.isBlank() && to.isBlank()

    // Such a range matches no flight; the pilot has to be told why, or the logbook just looks empty.
    val isRangeReversed: Boolean get() {
        val start = parseDate(from) ?: return false
        val end = parseDate(to) ?: return false
        return start > end
    }

    fun toFilter(): FlightFilter = FlightFilter(text, wingIds, parseDate(from), parseDate(to))

    fun toggle(wingId: WingId): LogbookSearch =
        copy(
            wingIds =
                if (wingId in
                    wingIds
                ) {
                    wingIds - wingId
                } else {
                    wingIds + wingId
                },
        )
}
