package ch.qscqlmpa.wingtracker.syride

import ch.qscqlmpa.wingtracker.flights.FlightType
import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import ch.qscqlmpa.wingtracker.flights.syride.SyrideFlight
import ch.qscqlmpa.wingtracker.flights.syride.syrideFlightId
import ch.qscqlmpa.wingtracker.flights.syride.syrideUntrackedFlightId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.minutes

// Syride has no API: these selectors read the activity cards its profile page loads while scrolling. They are the
// fragile part of the integration and are covered by tests on saved pages (ADR 0021).
private const val CARD = "div[id^=activite]"
private const val CARD_ID_PREFIX = "activite"
private const val FIELD = "ul"
private const val FIELD_VALUE = "font"
private const val TRACK_LINK = "[onclick*=showFlightInPopup]"
private const val SITE_LINK = "[id^=site] a"
private const val TYPE = "[id^=type]"

// The flight id only appears inside the script call that opens the track.
private val FLIGHT_ID = Regex("showFlightInPopup\\((\\d+)")
private val DATE = Regex("(?<day>\\d{2})/(?<month>\\d{2})/(?<year>\\d{4}) - (?<hour>\\d{2})h(?<minute>\\d{2})")
private val DURATION = Regex("(?:(\\d+)h)?\\s*(?:(\\d+)min)?")
private const val NOT_SET = "not set"
private const val MINUTES_PER_HOUR = 60

/** What one page of the activity feed contained. */
internal data class ParsedPage(
    val activityIds: List<Int>,
    val flights: List<SyrideFlight>,
    // Cards that look like flights but could not be understood; a rise in this number means the pages changed.
    val unreadableFlights: Int,
)

/** The value of a labelled line of a card, e.g. "Used wing : Hiko P (Niviuk)", and what follows it. */
private class Field(
    val value: String,
    val remark: String,
)

internal object SyrideActivityParser {
    fun parsePage(html: String): ParsedPage {
        val cards = Jsoup.parseBodyFragment(html).select(CARD)
        val ids = cards.map { it.activityId() }
        // Told by the card's text, not by its fields, so a change in their markup shows as unreadable flights.
        val flightCards = cards.filter { "Flight name" in it.text() }
        val flights = flightCards.mapNotNull { parseFlight(it) }
        return ParsedPage(ids, flights, flightCards.size - flights.size)
    }

    private fun parseFlight(card: Element): SyrideFlight? {
        val fields = card.fields()

        fun value(label: String): String? = fields[label]?.value?.takeIf { it.isNotEmpty() }

        val flightId =
            card
                .selectFirst(TRACK_LINK)
                ?.attr("onclick")
                ?.let { FLIGHT_ID.find(it) }
                ?.groupValues
                ?.get(1)
        val moment = value("Date")?.let { parseMoment(it) }
        val duration = minutes(value("Flight time"))?.takeIf { it > 0 }
        val wingName = value("Used wing")
        // A card without these is not a flight we can use; it is counted as unreadable by the caller.
        if (moment == null || duration == null || wingName == null) return null
        return SyrideFlight(
            // A flight without a track has no flight page, so its activity stands in as the stable id.
            id = if (flightId != null) syrideFlightId(flightId) else syrideUntrackedFlightId(card.activityId()),
            hasTrack = flightId != null,
            name = value("Flight name")?.takeIf { it != NOT_SET },
            date = moment.first,
            startTime = moment.second,
            duration = duration.minutes,
            siteName = card.selectFirst(SITE_LINK)?.cleanText()?.takeIf { it.isNotEmpty() } ?: value("Flying site"),
            // The brand follows the wing's name in parentheses, next to its logo.
            wing = SourceWing(wingName, fields["Used wing"]?.remark?.let(::insideParentheses)),
            distanceKm = number(value("Distance"), "km"),
            topAltitudeMeters = number(value("Top altitude"), "m")?.toInt(),
            instrument = value("Used instrument"),
            type = card.selectFirst(TYPE)?.cleanText()?.let(::flightType),
        )
    }

    // Followed by "without instrument." for a flight logged by hand, which the missing track already tells.
    private fun flightType(text: String): FlightType? =
        when {
            text.startsWith("Hike flight") -> FlightType.HikeAndFly
            text.startsWith("Site flight") -> FlightType.Site
            else -> null
        }

    private fun Element.activityId() = id().removePrefix(CARD_ID_PREFIX).toInt()

    // A field's own text is its label and colon, followed by anything written after the value.
    private fun Element.fields(): Map<String, Field> =
        select(FIELD).associate { field ->
            val own = field.ownText().replace(' ', ' ')
            own.substringBefore(':').trim() to
                Field(
                    value = field.selectFirst(FIELD_VALUE)?.cleanText().orEmpty(),
                    remark = own.substringAfter(':', "").trim(),
                )
        }

    private fun Element.cleanText() = text().replace(' ', ' ').trim()

    private fun insideParentheses(text: String): String? =
        text
            .substringAfter('(', "")
            .substringBefore(')')
            .trim()
            .takeIf { it.isNotEmpty() }

    private fun parseMoment(raw: String): Pair<LocalDate, LocalTime>? {
        val found = DATE.find(raw) ?: return null

        fun part(name: String) = checkNotNull(found.groups[name]).value.toInt()
        return LocalDate(part("year"), part("month"), part("day")) to LocalTime(part("hour"), part("minute"))
    }

    private fun minutes(raw: String?): Int? {
        val found = raw?.let { DURATION.matchEntire(it.trim()) } ?: return null
        if (found.groupValues[1].isEmpty() && found.groupValues[2].isEmpty()) return null
        return (found.groupValues[1].toIntOrNull() ?: 0) * MINUTES_PER_HOUR + (found.groupValues[2].toIntOrNull() ?: 0)
    }

    private fun number(
        raw: String?,
        unit: String,
    ): Double? =
        raw
            ?.let {
                Regex("([\\d.,]+)\\s*$unit").matchEntire(it.trim())
            }?.groupValues
            ?.get(1)
            ?.replace(",", ".")
            ?.toDoubleOrNull()
}
