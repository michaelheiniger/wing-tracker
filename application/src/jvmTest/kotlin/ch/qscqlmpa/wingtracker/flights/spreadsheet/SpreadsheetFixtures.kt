package ch.qscqlmpa.wingtracker.flights.spreadsheet

import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// Own factories for the spreadsheet import, modelled on a digitized paper logbook.
val LOGBOOK_HEADERS =
    listOf(
        "Flight no.",
        "Date",
        "Glider",
        "Start altitude (m)",
        "Takeoff",
        "Landing",
        "Duration (h)",
        "Duration (min)",
        "Altitude diff. (m)",
        "Distance (km)",
        "Remarks / Observations",
    )

val LOGBOOK_MAPPING =
    ColumnMapping(
        mapOf(
            FlightField.Date to 1,
            FlightField.Wing to 2,
            FlightField.TakeOff to 4,
            FlightField.Landing to 5,
            FlightField.DurationHours to 6,
            FlightField.DurationMinutes to 7,
            FlightField.Distance to 9,
            FlightField.Notes to 10,
        ),
    )

fun text(value: String) = Cell.Text(value)

fun number(value: Double) = Cell.Number(value)

/** A row as the logbook has it; every column can be replaced to make it what a test is about. */
fun aLogbookRow(
    number: Int = 1,
    date: Cell = Cell.Date(LocalDate(2023, 6, 2)),
    glider: Cell = text("Birdy"),
    takeOff: Cell = text("Sonchaux"),
    landing: Cell = text("Rennaz"),
    hours: Cell = Cell.Empty,
    minutes: Cell = number(20.0),
    distance: Cell = Cell.Empty,
    remarks: Cell = text("1er vol avec ma voile !"),
): List<Cell> =
    listOf(
        number(number.toDouble()),
        date,
        glider,
        number(1400.0),
        takeOff,
        landing,
        hours,
        minutes,
        number(1000.0),
        distance,
        remarks,
    )

fun aLogbookSheet(
    vararg rows: List<Cell>,
    name: String = "Flights",
) = Sheet(name, listOf(LOGBOOK_HEADERS.map(::text)) + rows.toList())

fun aSpreadsheetFlight(
    rowNumber: Int = 2,
    date: LocalDate = LocalDate(2023, 6, 2),
    wing: String = "Birdy",
    duration: Duration = 20.minutes,
) = SpreadsheetFlight(
    rowNumber = rowNumber,
    date = date,
    duration = duration,
    wing = SourceWing(wing, brand = null),
    takeOffSite = "Sonchaux",
    landingSite = "Rennaz",
    distanceKm = null,
    notes = "1er vol avec ma voile !",
)
