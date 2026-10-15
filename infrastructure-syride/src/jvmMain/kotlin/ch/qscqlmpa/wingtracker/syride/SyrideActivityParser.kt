package ch.qscqlmpa.wingtracker.syride

import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import ch.qscqlmpa.wingtracker.flights.syride.SyrideFlight
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.minutes

// Syride has no API: these patterns read the activity cards its profile page loads while scrolling. They are the
// fragile part of the integration and are covered by tests on saved pages (ADR 0021).
private val CARD_START = Regex("(?=<div id='activite\\d+')")
private val ACTIVITY_ID = Regex("<div id='activite(\\d+)'")
private val FLIGHT_ID = Regex("showFlightInPopup\\((\\d+)")
private val LABELLED = Regex("<ul[^>]*>\\s*([^<:]+?)\\s*:\\s*(.*?)</ul>", RegexOption.DOT_MATCHES_ALL)
private val DATE = Regex("(?<day>\\d{2})/(?<month>\\d{2})/(?<year>\\d{4}) - (?<hour>\\d{2})h(?<minute>\\d{2})")
private val DURATION = Regex("(?:(\\d+)h)?\\s*(?:(\\d+)min)?")
private val SITE_LINK = Regex("id='site\\d+'>\\s*at\\s*<a href='[^']+'>([^<]+)</a>")
private val BRAND = Regex("\\(([^<(]+?)\\s*<img")
private val TAG = Regex("<[^>]+>")
private const val NOT_SET = "not set"
private const val MINUTES_PER_HOUR = 60

/** What one page of the activity feed contained. */
internal data class ParsedPage(
    val activityIds: List<Int>,
    val flights: List<SyrideFlight>,
    // Cards that look like flights but could not be understood; a rise in this number means the pages changed.
    val unreadableFlights: Int,
)

internal object SyrideActivityParser {
    fun parsePage(html: String): ParsedPage {
        val cards = html.split(CARD_START).filter { it.startsWith("<div id='activite") }
        val ids = cards.map { checkNotNull(ACTIVITY_ID.find(it)).groupValues[1].toInt() }
        val flightCards = cards.filter { "Flight name" in it }
        val flights = flightCards.mapNotNull { parseFlight(it) }
        return ParsedPage(ids, flights, flightCards.size - flights.size)
    }

    private fun parseFlight(card: String): SyrideFlight? {
        val activityId = checkNotNull(ACTIVITY_ID.find(card)).groupValues[1]
        val values = LABELLED.findAll(card).associate { it.groupValues[1] to it.groupValues[2] }

        fun value(label: String): String? =
            values[label]?.let { text(it.substringBefore("(")) }?.takeIf { it.isNotEmpty() }

        val flightId = FLIGHT_ID.find(card)?.groupValues?.get(1)
        val moment = value("Date")?.let { parseMoment(it) }
        val duration = minutes(value("Flight time"))?.takeIf { it > 0 }
        val wingName = value("Used wing")
        // A card without these is not a flight we can use; it is counted as unreadable by the caller.
        if (moment == null || duration == null || wingName == null) return null
        return SyrideFlight(
            // A flight without a track has no flight page, so its activity stands in as the stable id.
            id = if (flightId != null) "flight:$flightId" else "activity:$activityId",
            hasTrack = flightId != null,
            name = value("Flight name")?.takeIf { it != NOT_SET },
            date = moment.first,
            startTime = moment.second,
            duration = duration.minutes,
            siteName = SITE_LINK.find(card)?.let { text(it.groupValues[1]) } ?: value("Flying site"),
            wing = SourceWing(wingName, values["Used wing"]?.let { BRAND.find(it) }?.let { text(it.groupValues[1]) }),
            distanceKm = number(value("Distance"), "km"),
            topAltitudeMeters = number(value("Top altitude"), "m")?.toInt(),
            instrument = value("Used instrument"),
        )
    }

    private fun parseMoment(raw: String): Pair<LocalDate, LocalTime>? {
        val found = DATE.find(raw) ?: return null

        fun part(name: String) = checkNotNull(found.groups[name]).value.toInt()
        return LocalDate(part("year"), part("month"), part("day")) to LocalTime(part("hour"), part("minute"))
    }

    private fun text(fragment: String): String =
        decodeEntities(fragment.replace(TAG, " ")).replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

    private fun decodeEntities(text: String): String =
        text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")

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
