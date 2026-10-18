package ch.qscqlmpa.wingtracker.flights.spreadsheet

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.InMemoryWings
import ch.qscqlmpa.wingtracker.flights.SiteNames
import ch.qscqlmpa.wingtracker.flights.SiteSource
import ch.qscqlmpa.wingtracker.flights.aFixedClock
import ch.qscqlmpa.wingtracker.flights.aFlight
import ch.qscqlmpa.wingtracker.flights.aWingInService
import ch.qscqlmpa.wingtracker.flights.importing.NewWingSpec
import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import ch.qscqlmpa.wingtracker.flights.importing.WingTarget
import ch.qscqlmpa.wingtracker.gear.wing.RegisterWing
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.shared.LoadResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class SpreadsheetImportTest {
    private val flights = mockk<FlightRepository>(relaxUnitFun = true)
    private val wings = InMemoryWings()
    private val saved = mutableListOf<Flight>()
    private val birdy = aWingInService(id = "w-birdy", name = "Birdy", manufacturer = "Niviuk", model = "Birdy")

    private fun givenStored(
        wingList: List<Wing> = listOf(birdy),
        flightList: List<Flight> = emptyList(),
    ) {
        wingList.forEach { wings.stored[it.id] = it }
        coEvery { flights.findAll() } returns LoadResult(flightList)
        coEvery { flights.save(any()) } coAnswers { saved += firstArg<Flight>() }
    }

    /** A flight already in the logbook, as an earlier import of [row] would have saved it. */
    private fun alreadyLogged(
        row: SpreadsheetFlight,
        wingId: WingId = birdy.id,
    ) = aFlight(id = "logged-${row.rowNumber}", details = row.toDetails(wingId))

    @Nested
    inner class Opening {
        private val reader = mockk<SpreadsheetReader>()

        @Test
        fun `prefers the sheet that looks like a logbook and suggests its columns`() =
            runTest {
                // given
                val summary = Sheet("Summary", listOf(listOf(text("Paragliding logbook – summary"))))
                coEvery { reader.read(any()) } returns listOf(summary, aLogbookSheet(aLogbookRow()))

                // when
                val opened = OpenSpreadsheet(reader)(byteArrayOf(1))

                // then
                assertThat(opened.preferred).isEqualTo(1)
                assertThat(opened.sheets[1].suggested).isEqualTo(LOGBOOK_MAPPING)
            }

        @Test
        fun `opens on the first sheet when none looks like a logbook`() =
            runTest {
                // given
                coEvery { reader.read(any()) } returns listOf(Sheet("Sheet1", emptyList()))

                // when / then
                assertThat(OpenSpreadsheet(reader)(byteArrayOf(1)).preferred).isZero()
            }
    }

    @Nested
    inner class Preparing {
        private val prepare get() = PrepareSpreadsheetImport(flights, wings)

        private fun readingOf(vararg flights: SpreadsheetFlight) =
            SheetReading(LOGBOOK_HEADERS, 1, flights.toList(), listOf(RowProblem(9, RowProblemKind.MissingDate)))

        @Test
        fun `groups the flights by wing whatever the spelling, most recently flown first, and keeps the problems`() =
            runTest {
                // given
                givenStored()
                val reading =
                    readingOf(
                        aSpreadsheetFlight(2, LocalDate(2023, 5, 20), "Alpha"),
                        aSpreadsheetFlight(3, LocalDate(2024, 8, 1), "Birdy"),
                        aSpreadsheetFlight(4, LocalDate(2024, 8, 2), "birdy "),
                    )

                // when
                val draft = prepare(reading)

                // then
                assertThat(draft.groups.map { it.wing.name }).containsExactly("Birdy", "Alpha")
                assertThat(
                    draft.groups
                        .first()
                        .flights
                        .map { it.wing },
                ).containsOnly(SourceWing("Birdy", null))
                assertThat(draft.problems).containsExactly(RowProblem(9, RowProblemKind.MissingDate))
            }

        @Test
        fun `suggests the existing wing with that name`() =
            runTest {
                // given
                givenStored()

                // when
                val draft = prepare(readingOf(aSpreadsheetFlight(wing = "Birdy")))

                // then
                assertThat(draft.groups.single().suggestedWingId).isEqualTo(birdy.id)
            }

        @Test
        fun `suggests the existing wing whose manufacturer and model make the name`() =
            runTest {
                // given
                val ultralite =
                    aWingInService(id = "w-ul4", name = "Light one", manufacturer = "Ozone", model = "Ultralite 4")
                givenStored(listOf(birdy, ultralite))

                // when
                val draft = prepare(readingOf(aSpreadsheetFlight(wing = "Ozone Ultralite 4")))

                // then
                assertThat(draft.groups.single().suggestedWingId).isEqualTo(ultralite.id)
            }
    }

    @Nested
    inner class LogbookContentMatching {
        @Test
        fun `matches identical flights by count, so only the extra ones are new`() {
            // given
            val first = aSpreadsheetFlight(rowNumber = 2)
            val second = aSpreadsheetFlight(rowNumber = 3)
            val third = aSpreadsheetFlight(rowNumber = 4)
            val logbook = LogbookContent(listOf(alreadyLogged(first), alreadyLogged(second)))

            // when
            val present = logbook.alreadyPresent(listOf(first to birdy.id, second to birdy.id, third to birdy.id))

            // then
            assertThat(present).containsExactlyInAnyOrder(first, second)
        }

        @Test
        fun `ignores case and spacing in the sites and leaves the notes out`() {
            // given
            val row = aSpreadsheetFlight()
            val edited =
                aFlight(
                    details =
                        FlightDetails(
                            row.date,
                            row.duration,
                            birdy.id,
                            SiteNames.of(SiteSource.Manual, "sonchaux"),
                            SiteNames.of(SiteSource.Manual, "RENNAZ"),
                            notes = "Edited later",
                        ),
                )

            // when / then
            assertThat(LogbookContent(listOf(edited)).alreadyPresent(listOf(row to birdy.id))).containsExactly(row)
        }

        @Test
        fun `does not match a flight on another wing or of another length`() {
            // given
            val row = aSpreadsheetFlight()
            val logbook =
                LogbookContent(
                    listOf(alreadyLogged(row, WingId("w-other")), alreadyLogged(row.copy(duration = 25.minutes))),
                )

            // when / then
            assertThat(logbook.alreadyPresent(listOf(row to birdy.id))).isEmpty()
        }
    }

    @Nested
    inner class Importing {
        private var next = 0
        private val import get() =
            ImportSpreadsheetFlights(flights, wings, aFixedClock(), RegisterWing(wings) { WingId("new-${next++}") }) {
                FlightId("imported-${next++}")
            }

        @Test
        fun `imports a row on an existing wing with everything the sheet gives`() =
            runTest {
                // given
                givenStored()
                val row = aSpreadsheetFlight().copy(distanceKm = 4.5)

                // when
                val result = import(listOf(row to WingTarget.Existing(birdy.id)), emptyMap())

                // then
                assertThat(result.imported).isEqualTo(1)
                assertThat(saved.single().details).usingRecursiveComparison().isEqualTo(row.toDetails(birdy.id))
                assertThat(saved.single().importedFrom).isNull()
            }

        @Test
        fun `records the spreadsheet as the source of the site names`() =
            runTest {
                // given
                givenStored()
                val row = aSpreadsheetFlight()

                // when
                import(listOf(row to WingTarget.Existing(birdy.id)), emptyMap())

                // then
                val details = saved.single().details
                assertThat(details.takeOffSite).isEqualTo(SiteNames.of(SiteSource.Spreadsheet, "Sonchaux"))
                assertThat(details.landingSite).isEqualTo(SiteNames.of(SiteSource.Spreadsheet, "Rennaz"))
            }

        @Test
        fun `leaves out rows already in the logbook and counts them as skipped`() =
            runTest {
                // given
                val first = aSpreadsheetFlight(rowNumber = 2)
                val second = aSpreadsheetFlight(rowNumber = 3)
                givenStored(flightList = listOf(alreadyLogged(first)))

                // when
                val result =
                    import(
                        listOf(first to WingTarget.Existing(birdy.id), second to WingTarget.Existing(birdy.id)),
                        emptyMap(),
                    )

                // then
                assertThat(result.imported).isEqualTo(1)
                assertThat(result.skipped).isEqualTo(1)
            }

        @Test
        fun `creates a new wing for the rows sent to it`() =
            runTest {
                // given
                givenStored()
                val rows = listOf(aSpreadsheetFlight(2, wing = "Alpha"), aSpreadsheetFlight(3, wing = "Alpha"))

                // when
                val result =
                    import(
                        rows.map { it to WingTarget.New("alpha") },
                        mapOf("alpha" to NewWingSpec("Alpha", "Gin", "Alpha")),
                    )

                // then
                val wing = result.createdWings.single()
                assertThat(wing.details.manufacturer).isEqualTo("Gin")
                assertThat(saved.map { it.details.wingId }).containsOnly(wing.id)
            }

        @Test
        fun `logs the rows of a day in the order of the sheet`() =
            runTest {
                // given
                givenStored()
                val later = aSpreadsheetFlight(rowNumber = 5, duration = 15.minutes)
                val earlier = aSpreadsheetFlight(rowNumber = 4, duration = 10.minutes)

                // when
                import(listOf(later, earlier).map { it to WingTarget.Existing(birdy.id) }, emptyMap())

                // then
                assertThat(saved.map { it.details.duration }).containsExactly(10.minutes, 15.minutes)
                assertThat(saved[0].loggedAt).isLessThan(saved[1].loggedAt)
            }

        @Test
        fun `does not import the rows of a skipped wing`() =
            runTest {
                // given
                givenStored()

                // when
                val result = import(listOf(aSpreadsheetFlight() to WingTarget.Skip), emptyMap())

                // then
                assertThat(result.imported).isZero()
                assertThat(result.skipped).isEqualTo(1)
                assertThat(saved).isEmpty()
            }
    }
}
