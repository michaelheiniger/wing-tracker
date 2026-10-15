package ch.qscqlmpa.wingtracker.syride

import ch.qscqlmpa.wingtracker.flights.syride.EarlierReading

// The patterns of the reader used until 2026-10-16, kept as they were: they define the garbled texts that flights it
// imported still carry, so those can be told apart and corrected. Remove once such flights no longer matter.
private val CARD_START = Regex("(?=<div id='activite\\d+')")
private val ACTIVITY_ID = Regex("<div id='activite(\\d+)'")
private val LABELLED = Regex("<ul[^>]*>\\s*([^<:]+?)\\s*:\\s*(.*?)</ul>", RegexOption.DOT_MATCHES_ALL)
private val SITE_LINK = Regex("id='site\\d+'>\\s*at\\s*<a href='[^']+'>([^<]+)</a>")
private val TAG = Regex("<[^>]+>")
private val SPACES = Regex("\\s+")
private const val NOT_SET = "not set"

/** What the earlier reader made of each flight card's name and site, by activity id. */
internal object EarlierSyrideReader {
    fun readPage(html: String): Map<Int, EarlierReading> =
        html
            .split(CARD_START)
            .mapNotNull { card -> ACTIVITY_ID.find(card)?.let { it.groupValues[1].toInt() to read(card) } }
            .toMap()

    private fun read(card: String): EarlierReading {
        val values = LABELLED.findAll(card).associate { it.groupValues[1] to it.groupValues[2] }

        fun value(label: String): String? =
            values[label]?.let { text(it.substringBefore("(")) }?.takeIf { it.isNotEmpty() }

        return EarlierReading(
            name = value("Flight name")?.takeIf { it != NOT_SET },
            siteName = SITE_LINK.find(card)?.let { text(it.groupValues[1]) } ?: value("Flying site"),
        )
    }

    private fun text(fragment: String): String =
        decodeEntities(fragment.replace(TAG, " ")).replace(' ', ' ').replace(SPACES, " ").trim()

    private fun decodeEntities(text: String): String =
        text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
}
