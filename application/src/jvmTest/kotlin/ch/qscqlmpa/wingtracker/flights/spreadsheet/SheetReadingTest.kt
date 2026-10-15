package ch.qscqlmpa.wingtracker.flights.spreadsheet

import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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
        fun `adds the hours and the minutes up`() {
            // given
            val sheet = aLogbookSheet(aLogbookRow(hours = number(1.0), minutes = number(30.0)))

            // when / then
            assertThat(readSheet(sheet, LOGBOOK_MAPPING).flights.single().duration).isEqualTo(90.minutes)
        }

        @Test
        fun `takes a cell formatted as a time as it is`() {
            // given
            val sheet = aLogbookSheet(aLogbookRow(minutes = Cell.Time(75.minutes)))

            // when / then
            assertThat(readSheet(sheet, LOGBOOK_MAPPING).flights.single().duration).isEqualTo(75.minutes)
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
    inner class DurationText {
        @ParameterizedTest(name = "\"{0}\" in the minutes column is {1} minutes")
        @CsvSource("20, 20", "1:30, 90", "1h30, 90", "2h, 120", "'12,5', 12.5")
        fun `reads hand-written durations`(
            written: String,
            minutes: Double,
        ) {
            // given
            val sheet = aLogbookSheet(aLogbookRow(minutes = text(written)))

            // when
            val duration = readSheet(sheet, LOGBOOK_MAPPING).flights.single().duration

            // then
            assertThat(duration).isEqualTo(minutes.minutes)
        }

        @Test
        fun `reads a fraction of an hour to the second`() {
            // given
            val sheet = aLogbookSheet(aLogbookRow(hours = number(1.0 / 3), minutes = Cell.Empty))

            // when / then
            assertThat(readSheet(sheet, LOGBOOK_MAPPING).flights.single().duration).isEqualTo(20.minutes)
        }

        @Test
        fun `reads a plain number in the hours column as hours`() {
            // given
            val sheet = aLogbookSheet(aLogbookRow(hours = text("1.5"), minutes = Cell.Empty))

            // when
            val duration: Duration = readSheet(sheet, LOGBOOK_MAPPING).flights.single().duration

            // then
            assertThat(duration).isEqualTo(1.5.hours)
        }
    }
}
