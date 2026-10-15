package ch.qscqlmpa.wingtracker.flights.spreadsheet

import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SheetReadingTest {
    @Nested
    inner class ReadSheet {
        @Test
        fun `makes a flight of a row with everything the mapping names`() {
            // given
            val sheet = aLogbookSheet(aLogbookRow(distance = number(4.5)))

            // when
            val reading = readSheet(sheet, LOGBOOK_MAPPING)

            // then
            assertThat(reading.flights.single())
                .usingRecursiveComparison()
                .isEqualTo(aSpreadsheetFlight().copy(distanceKm = 4.5))
            assertThat(reading.problems).isEmpty()
        }

        @Test
        fun `takes the first row with a value as the header and numbers rows as the sheet shows them`() {
            // given
            val sheet = Sheet("Flights", listOf(emptyList(), LOGBOOK_HEADERS.map(::text), aLogbookRow()))

            // when
            val reading = readSheet(sheet, LOGBOOK_MAPPING)

            // then
            assertThat(reading.headers).isEqualTo(LOGBOOK_HEADERS)
            assertThat(reading.headerRowNumber).isEqualTo(2)
            assertThat(reading.flights.single().rowNumber).isEqualTo(3)
        }

        @Test
        fun `reads text dates written day first and numbers with a Swiss thousands mark`() {
            // given
            val sheet = aLogbookSheet(aLogbookRow(date = text("30.12.2023"), distance = text("1'200")))

            // when
            val flight = readSheet(sheet, LOGBOOK_MAPPING).flights.single()

            // then
            assertThat(flight.date).isEqualTo(LocalDate(2023, 12, 30))
            assertThat(flight.distanceKm).isEqualTo(1200.0)
        }

        @Test
        fun `leaves fully empty rows out without a word`() {
            // given
            val sheet = aLogbookSheet(aLogbookRow(), List(LOGBOOK_HEADERS.size) { Cell.Empty })

            // when
            val reading = readSheet(sheet, LOGBOOK_MAPPING)

            // then
            assertThat(reading.flights).hasSize(1)
            assertThat(reading.problems).isEmpty()
        }

        @Test
        fun `reports each row that cannot make a flight with what is wrong`() {
            // given
            val sheet =
                aLogbookSheet(
                    aLogbookRow(date = Cell.Empty),
                    aLogbookRow(date = text("sometime in June")),
                    aLogbookRow(glider = text(" ")),
                    aLogbookRow(minutes = Cell.Empty),
                    aLogbookRow(minutes = text("long")),
                    aLogbookRow(distance = text("far")),
                )

            // when
            val reading = readSheet(sheet, LOGBOOK_MAPPING)

            // then
            assertThat(reading.flights).isEmpty()
            assertThat(reading.problems).containsExactly(
                RowProblem(2, RowProblemKind.MissingDate),
                RowProblem(3, RowProblemKind.UnreadableDate),
                RowProblem(4, RowProblemKind.MissingWing),
                RowProblem(5, RowProblemKind.MissingDuration),
                RowProblem(6, RowProblemKind.UnreadableDuration),
                RowProblem(7, RowProblemKind.UnreadableDistance),
            )
        }

        @Test
        fun `reads no row until the mapping is complete, but still gives the headers`() {
            // given
            val sheet = aLogbookSheet(aLogbookRow())

            // when
            val reading = readSheet(sheet, LOGBOOK_MAPPING.with(FlightField.Wing, null))

            // then
            assertThat(reading.headers).isEqualTo(LOGBOOK_HEADERS)
            assertThat(reading.flights).isEmpty()
        }
    }

    @Nested
    inner class Durations {
        @ParameterizedTest(name = "{0} h and {1} min make {2} min")
        @CsvSource("'', 20, 20", "1, 30, 90", "1, '', 60", "'', 60, 60", "2, 75, 195")
        fun `adds whole hours and whole minutes up, minutes past an hour included`(
            hours: String,
            minutes: String,
            total: Int,
        ) {
            // given
            val sheet = aLogbookSheet(aLogbookRow(hours = hours.asCell(), minutes = minutes.asCell()))

            // when
            val duration = readSheet(sheet, LOGBOOK_MAPPING).flights.single().duration

            // then
            assertThat(duration).isEqualTo(total.minutes)
        }

        @Test
        fun `reads whole numbers typed as text`() {
            // given
            val sheet = aLogbookSheet(aLogbookRow(hours = text(" 1 "), minutes = text("30")))

            // when / then
            assertThat(readSheet(sheet, LOGBOOK_MAPPING).flights.single().duration).isEqualTo(90.minutes)
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("ch.qscqlmpa.wingtracker.flights.spreadsheet.SheetReadingTest#notWholeUnits")
        fun `reports anything but a whole number instead of guessing`(cell: Cell) {
            // given
            val sheet = aLogbookSheet(aLogbookRow(minutes = cell))

            // when
            val reading = readSheet(sheet, LOGBOOK_MAPPING)

            // then
            assertThat(reading.problems).containsExactly(RowProblem(2, RowProblemKind.UnreadableDuration))
        }

        private fun String.asCell(): Cell = if (isEmpty()) Cell.Empty else number(toDouble())
    }

    companion object {
        @JvmStatic
        fun notWholeUnits() =
            listOf(number(12.5), number(-10.0), text("1:30"), text("1h30"), text("long"), Cell.Time(90.minutes))
    }
}
