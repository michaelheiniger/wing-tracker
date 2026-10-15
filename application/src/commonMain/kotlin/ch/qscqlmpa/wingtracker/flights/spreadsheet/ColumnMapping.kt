package ch.qscqlmpa.wingtracker.flights.spreadsheet

/** What a column of a logbook spreadsheet can hold. */
enum class FlightField {
    Date,
    Wing,

    // Hours and minutes are added up, since paper logbooks often split the duration into two columns.
    DurationHours,
    DurationMinutes,
    TakeOff,
    Landing,
    Distance,
    Notes,
}

/** Which column (by index) holds which field; a field without a column is not imported. */
data class ColumnMapping(
    val columns: Map<FlightField, Int> = emptyMap(),
) {
    fun columnOf(field: FlightField): Int? = columns[field]

    fun with(
        field: FlightField,
        column: Int?,
    ): ColumnMapping {
        // A column holds one field, so giving it to another field takes it away from the first.
        val others = columns.filterValues { it != column } - field
        return ColumnMapping(if (column == null) others else others + (field to column))
    }

    /** Enough to make a flight: when, on what, and for how long. */
    val isComplete: Boolean
        get() =
            FlightField.Date in columns &&
                FlightField.Wing in columns &&
                (FlightField.DurationHours in columns || FlightField.DurationMinutes in columns)

    companion object {
        /**
         * Guesses the mapping from the header row, in English, French and German. Only a guess: the pilot sees it and
         * corrects it before anything is read.
         */
        fun suggest(headers: List<String>): ColumnMapping {
            var mapping = ColumnMapping()
            headers.forEachIndexed { index, header ->
                val field = fieldOf(normalize(header)) ?: return@forEachIndexed
                if (mapping.columnOf(field) == null) mapping = mapping.with(field, index)
            }
            return mapping
        }

        private fun fieldOf(header: String): FlightField? {
            val words = header.split(' ').filter { it.isNotEmpty() }.toSet()
            return when {
                header.isEmpty() -> null

                // Altitudes come first: "start altitude" is about the take-off, but is not the take-off site.
                words.any { it in ALTITUDE_WORDS } -> null

                words.any { it in DURATION_WORDS } -> durationField(words)

                else -> FIELD_WORDS.entries.firstOrNull { (_, synonyms) -> synonyms.any { it in words } }?.key
            }
        }

        // Without a unit, minutes: a hand-written duration is more often "20" than "0.33". 1:30 reads either way.
        private fun durationField(words: Set<String>) =
            if (words.any { it in HOUR_WORDS }) FlightField.DurationHours else FlightField.DurationMinutes

        private fun normalize(header: String): String =
            header
                .lowercase()
                .map { ACCENTS[it] ?: it }
                .joinToString("")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()

        private val ACCENTS =
            mapOf(
                'é' to 'e',
                'è' to 'e',
                'ê' to 'e',
                'à' to 'a',
                'â' to 'a',
                'ä' to 'a',
                'ö' to 'o',
                'ô' to 'o',
                'ü' to 'u',
                'û' to 'u',
                'ç' to 'c',
                'î' to 'i',
            )
        private val ALTITUDE_WORDS =
            setOf("altitude", "alt", "hohe", "hoehe", "hauteur", "denivele", "elevation", "height")
        private val DURATION_WORDS = setOf("duration", "duree", "dauer", "flugzeit", "airtime", "time", "temps")
        private val HOUR_WORDS = setOf("h", "hours", "hour", "hrs", "heures", "heure", "stunden", "std")
        private val FIELD_WORDS =
            linkedMapOf(
                FlightField.Date to setOf("date", "datum", "day", "jour"),
                FlightField.Wing to setOf("glider", "wing", "voile", "aile", "schirm", "gleitschirm", "parapente"),
                FlightField.TakeOff to
                    setOf(
                        "takeoff",
                        "take",
                        "launch",
                        "decollage",
                        "deco",
                        "startplatz",
                        "startort",
                    ),
                FlightField.Landing to setOf("landing", "atterrissage", "attero", "landeplatz", "landung"),
                FlightField.Distance to setOf("distance", "km", "strecke", "distanz"),
                FlightField.Notes to
                    setOf(
                        "remarks",
                        "remark",
                        "notes",
                        "note",
                        "comments",
                        "comment",
                        "observations",
                        "remarques",
                        "bemerkungen",
                        "notizen",
                        "kommentar",
                    ),
            )
    }
}
