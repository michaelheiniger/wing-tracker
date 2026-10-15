package ch.qscqlmpa.wingtracker.flights.spreadsheet

import ch.qscqlmpa.wingtracker.flights.ExternalReference
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.importing.ImportableFlight
import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What one row of a logbook spreadsheet turned out to be. */
sealed interface SheetRow {
    // The row as the spreadsheet shows it, so the pilot can find it.
    val rowNumber: Int
}

/** A row that makes a flight. */
data class SpreadsheetFlight(
    override val rowNumber: Int,
    override val date: LocalDate,
    override val duration: Duration,
    override val wing: SourceWing,
    val takeOffSite: String?,
    val landingSite: String?,
    val distanceKm: Double?,
    val notes: String?,
) : SheetRow,
    ImportableFlight {
    // A spreadsheet has no ids; rows imported before are recognised by their content instead (LogbookContent).
    override val externalReference: ExternalReference? get() = null

    override fun toDetails(wingId: WingId) =
        FlightDetails(
            date = date,
            duration = duration,
            wingId = wingId,
            takeOffSite = takeOffSite,
            landingSite = landingSite,
            notes = notes,
            distanceKm = distanceKm,
        )
}

enum class RowProblemKind {
    MissingDate,
    UnreadableDate,
    MissingWing,
    MissingDuration,
    UnreadableDuration,
    UnreadableDistance,
}

data class RowProblem(
    override val rowNumber: Int,
    val kind: RowProblemKind,
) : SheetRow

/** What a sheet holds under a mapping: the header, the rows that make flights, and the rows that do not. */
data class SheetReading(
    val headers: List<String>,
    // The row the headers were taken from; data starts below it.
    val headerRowNumber: Int,
    val flights: List<SpreadsheetFlight>,
    val problems: List<RowProblem>,
)

/**
 * Reads a sheet whose first non-empty row is the header. Rows without a single value are left out silently; any
 * other row either makes a flight or is reported with what is wrong, so nothing disappears unnoticed.
 */
fun readSheet(
    sheet: Sheet,
    mapping: ColumnMapping,
): SheetReading {
    val headerIndex = sheet.rows.indexOfFirst { row -> row.any { it != Cell.Empty } }
    if (headerIndex < 0) return SheetReading(emptyList(), 0, emptyList(), emptyList())
    val headers = sheet.rows[headerIndex].map { it.asText().orEmpty() }
    val flights = mutableListOf<SpreadsheetFlight>()
    val problems = mutableListOf<RowProblem>()
    if (mapping.isComplete) {
        sheet.rows.withIndex().drop(headerIndex + 1).forEach { (index, row) ->
            if (row.all { it == Cell.Empty }) return@forEach
            when (val read = RowReader(row, mapping).read(index + 1)) {
                is SpreadsheetFlight -> flights += read
                is RowProblem -> problems += read
            }
        }
    }
    return SheetReading(headers, headerIndex + 1, flights, problems)
}

private class RowReader(
    private val row: List<Cell>,
    private val mapping: ColumnMapping,
) {
    fun read(rowNumber: Int): SheetRow {
        fun problem(kind: RowProblemKind) = RowProblem(rowNumber, kind)
        val dateCell = cell(FlightField.Date)
        val date = dateCell.asDate()
        val wing = cell(FlightField.Wing).asText()
        val hours = cell(FlightField.DurationHours).asDuration(1.hours)
        val minutes = cell(FlightField.DurationMinutes).asDuration(1.minutes)
        val distanceCell = cell(FlightField.Distance)
        val distance = distanceCell.asNumber()?.takeIf { it >= 0 }
        return when {
            dateCell == Cell.Empty -> {
                problem(RowProblemKind.MissingDate)
            }

            date == null -> {
                problem(RowProblemKind.UnreadableDate)
            }

            wing == null -> {
                problem(RowProblemKind.MissingWing)
            }

            hours is Unreadable || minutes is Unreadable -> {
                problem(RowProblemKind.UnreadableDuration)
            }

            distanceCell != Cell.Empty && distance == null -> {
                problem(RowProblemKind.UnreadableDistance)
            }

            else -> {
                val duration = (hours as Read).value + (minutes as Read).value
                if (!duration.isPositive()) {
                    problem(RowProblemKind.MissingDuration)
                } else {
                    SpreadsheetFlight(
                        rowNumber = rowNumber,
                        date = date,
                        duration = duration,
                        wing = SourceWing(wing, brand = null),
                        takeOffSite = cell(FlightField.TakeOff).asText(),
                        landingSite = cell(FlightField.Landing).asText(),
                        distanceKm = distance,
                        notes = cell(FlightField.Notes).asText(),
                    )
                }
            }
        }
    }

    private fun cell(field: FlightField): Cell = mapping.columnOf(field)?.let { row.getOrNull(it) } ?: Cell.Empty
}

private sealed interface DurationRead

private data class Read(
    val value: Duration,
) : DurationRead

private data object Unreadable : DurationRead

/** An empty cell counts as zero, so a row may give only hours or only minutes. */
private fun Cell.asDuration(unit: Duration): DurationRead =
    when (this) {
        Cell.Empty -> Read(Duration.ZERO)
        is Cell.Time -> Read(value)
        is Cell.Number -> if (value >= 0) Read(unit * value) else Unreadable
        is Cell.Text -> parseDuration(value.trim(), unit)?.let(::Read) ?: Unreadable
        is Cell.Date -> Unreadable
    }.let { read -> if (read is Read) Read(read.value.roundedToSeconds()) else read }

// Hours given as a fraction rarely come out even; a flight time never needs more than seconds.
private fun Duration.roundedToSeconds(): Duration =
    inWholeMilliseconds
        .toDouble()
        .div(MILLIS_PER_SECOND)
        .roundToLong()
        .seconds

// "1:30" and "1h30" are hours and minutes whatever the column; a plain number is in the column's unit.
private fun parseDuration(
    text: String,
    unit: Duration,
): Duration? {
    if (text.isEmpty()) return Duration.ZERO
    HOURS_MINUTES.matchEntire(text.lowercase())?.let { match ->
        val (h, m) = match.destructured
        return h.toInt().hours + (m.ifEmpty { "0" }).toInt().minutes
    }
    return parseNumber(text)?.takeIf { it >= 0 }?.let { unit * it }
}

private fun Cell.asText(): String? =
    when (this) {
        Cell.Empty -> null

        is Cell.Text -> value.trim().ifEmpty { null }

        // A wing or a site can be named by a number; it must not read "4.0".
        is Cell.Number -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

        is Cell.Date -> value.toString()

        is Cell.Time -> value.toString()
    }

private fun Cell.asNumber(): Double? =
    when (this) {
        is Cell.Number -> value
        is Cell.Text -> parseNumber(value)
        else -> null
    }

private fun Cell.asDate(): LocalDate? =
    when (this) {
        is Cell.Date -> value
        is Cell.Text -> parseDate(value.trim())
        else -> null
    }

// Thousands separators as written in Switzerland, France and Germany, and a comma as the decimal mark.
private fun parseNumber(text: String): Double? =
    text
        .replace("'", "")
        .replace("’", "")
        .replace(" ", "")
        .replace(',', '.')
        .toDoubleOrNull()

/** Day first, as written in Europe; an ISO date also works. A date typed as a date in the file needs none of this. */
private fun parseDate(text: String): LocalDate? {
    val iso = ISO_DATE.matchEntire(text)?.destructured
    val dayFirst = DAY_FIRST_DATE.matchEntire(text)?.destructured
    return when {
        iso != null -> {
            val (y, m, d) = iso
            dateOrNull(y.toInt(), m.toInt(), d.toInt())
        }

        dayFirst != null -> {
            val (d, m, y) = dayFirst
            dateOrNull(if (y.length == 2) CENTURY + y.toInt() else y.toInt(), m.toInt(), d.toInt())
        }

        else -> {
            null
        }
    }
}

private fun dateOrNull(
    year: Int,
    month: Int,
    day: Int,
): LocalDate? = runCatching { LocalDate(year, month, day) }.getOrNull()

private const val MILLIS_PER_SECOND = 1000.0
private const val CENTURY = 2000
private val HOURS_MINUTES = Regex("""(\d{1,2})\s*[:h]\s*(\d{0,2})(?::\d{2})?\s*(?:min)?""")
private val ISO_DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
private val DAY_FIRST_DATE = Regex("""(\d{1,2})[./-](\d{1,2})[./-](\d{2}|\d{4})""")
