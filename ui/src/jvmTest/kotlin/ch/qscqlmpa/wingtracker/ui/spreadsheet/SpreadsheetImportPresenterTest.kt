package ch.qscqlmpa.wingtracker.ui.spreadsheet

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.importing.ImportResult
import ch.qscqlmpa.wingtracker.flights.importing.NewWingSpec
import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import ch.qscqlmpa.wingtracker.flights.importing.WingGroup
import ch.qscqlmpa.wingtracker.flights.importing.WingTarget
import ch.qscqlmpa.wingtracker.flights.spreadsheet.Cell
import ch.qscqlmpa.wingtracker.flights.spreadsheet.ColumnMapping
import ch.qscqlmpa.wingtracker.flights.spreadsheet.FlightField
import ch.qscqlmpa.wingtracker.flights.spreadsheet.ImportSpreadsheetFlights
import ch.qscqlmpa.wingtracker.flights.spreadsheet.LogbookContent
import ch.qscqlmpa.wingtracker.flights.spreadsheet.OpenSpreadsheet
import ch.qscqlmpa.wingtracker.flights.spreadsheet.OpenedSpreadsheet
import ch.qscqlmpa.wingtracker.flights.spreadsheet.PrepareSpreadsheetImport
import ch.qscqlmpa.wingtracker.flights.spreadsheet.Sheet
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SheetChoice
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SheetReading
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetException
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetFlight
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetImportDraft
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetProblem
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.flights.importing.WingChoice
import ch.qscqlmpa.wingtracker.ui.platform.FilePicker
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val HEADERS = listOf("Date", "Glider", "Takeoff", "Landing", "Duration (min)", "Start altitude (m)")
private val SUGGESTED =
    ColumnMapping(
        mapOf(
            FlightField.Date to 0,
            FlightField.Wing to 1,
            FlightField.TakeOff to 2,
            FlightField.Landing to 3,
            FlightField.DurationMinutes to 4,
        ),
    )

private fun aRow(
    glider: String = "Birdy",
    minutes: Double = 20.0,
) = listOf(
    Cell.Date(LocalDate(2023, 6, 2)),
    Cell.Text(glider),
    Cell.Text("Sonchaux"),
    Cell.Text("Rennaz"),
    Cell.Number(minutes),
    Cell.Number(1400.0),
)

private val FLIGHTS_SHEET = Sheet("Flights", listOf(HEADERS.map { Cell.Text(it) }, aRow(), aRow("Alpha")))
private val OPENED =
    OpenedSpreadsheet(
        listOf(SheetChoice(Sheet("Summary", emptyList()), ColumnMapping()), SheetChoice(FLIGHTS_SHEET, SUGGESTED)),
        preferred = 1,
    )
private val FILE = NewAttachment("digitized-logbook.ods", byteArrayOf(1, 2, 3))
private val BIRDY = WingId("w-birdy")

private fun aFlight(
    rowNumber: Int,
    wing: String = "Birdy",
) = SpreadsheetFlight(
    rowNumber,
    LocalDate(2023, 6, 2),
    20.minutes,
    SourceWing(wing, null),
    "Sonchaux",
    "Rennaz",
    null,
    null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SpreadsheetImportPresenterTest {
    private val picker = mockk<FilePicker>()
    private val open = mockk<OpenSpreadsheet>()
    private val prepare = mockk<PrepareSpreadsheetImport>()
    private val import = mockk<ImportSpreadsheetFlights>()

    private fun TestScope.presenter() =
        SpreadsheetImportPresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            picker,
            "Choose",
            open,
            prepare,
            import,
        )

    private fun givenFile() {
        coEvery { picker.pick("Choose", setOf("ods", "xlsx")) } returns listOf(FILE)
        coEvery { open(FILE.bytes) } returns OPENED
    }

    private fun TestScope.presenterOnWings(
        vararg groups: WingGroup<SpreadsheetFlight>,
        logbook: List<Flight> = emptyList(),
    ): SpreadsheetImportPresenter {
        givenFile()
        coEvery { prepare(any()) } returns
            SpreadsheetImportDraft(groups.toList(), emptyList(), emptyList(), LogbookContent(logbook))
        return presenter().apply {
            choose {}
            next()
        }
    }

    @Nested
    inner class Choosing {
        @Test
        fun `opens on the sheet that looks like a logbook, with the columns its header suggests`() =
            runTest {
                // given
                givenFile()
                val presenter = presenter()

                // when
                presenter.choose {}

                // then
                assertThat(presenter.stage).isEqualTo(SpreadsheetStage.Columns)
                assertThat(presenter.sheetIndex).isEqualTo(1)
                assertThat(presenter.columns).isEqualTo(SUGGESTED)
                assertThat(presenter.reading?.flights).hasSize(2)
                assertThat(presenter.canContinue).isTrue()
            }

        @Test
        fun `tells when no file was chosen`() =
            runTest {
                // given
                coEvery { picker.pick(any(), any()) } returns emptyList()
                var cancelled = false

                // when
                presenter().choose { cancelled = true }

                // then
                assertThat(cancelled).isTrue()
            }

        @Test
        fun `explains an Excel 97-2003 file and stays on the file step`() =
            runTest {
                // given
                coEvery { picker.pick(any(), any()) } returns listOf(FILE)
                coEvery { open(any()) } throws SpreadsheetException(SpreadsheetProblem.OldExcelFormat)
                val presenter = presenter()

                // when
                presenter.choose {}

                // then
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.SpreadsheetOldFormat)
                assertThat(presenter.stage).isEqualTo(SpreadsheetStage.File)
            }

        @Test
        fun `reports any other trouble reading the file as unreadable`() =
            runTest {
                // given
                coEvery { picker.pick(any(), any()) } throws IOException("Permission denied")
                val presenter = presenter()

                // when
                presenter.choose {}

                // then
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.SpreadsheetUnreadable)
                assertThat(presenter.failure?.detail).isEqualTo("Permission denied")
            }
    }

    @Nested
    inner class ChoosingColumns {
        @Test
        fun `reads the sheet again when a column changes`() =
            runTest {
                // given
                givenFile()
                val presenter = presenter()
                presenter.choose {}

                // when
                presenter.assign(FlightField.Notes, 5)

                // then
                assertThat(
                    presenter.reading
                        ?.flights
                        ?.first()
                        ?.notes,
                ).isEqualTo("1400")
            }

        @Test
        fun `cannot go on without a duration column`() =
            runTest {
                // given
                givenFile()
                val presenter = presenter()
                presenter.choose {}

                // when
                presenter.assign(FlightField.DurationMinutes, null)

                // then
                assertThat(presenter.canContinue).isFalse()
            }

        @Test
        fun `takes the suggestion of another sheet when it is selected`() =
            runTest {
                // given
                givenFile()
                val presenter = presenter()
                presenter.choose {}

                // when
                presenter.selectSheet(0)

                // then
                assertThat(presenter.columns).isEqualTo(ColumnMapping())
                assertThat(presenter.canContinue).isFalse()
            }

        @Test
        fun `prepares the import from what the columns read`() =
            runTest {
                // given
                givenFile()
                val reading = slot<SheetReading>()
                coEvery { prepare(capture(reading)) } returns
                    SpreadsheetImportDraft(emptyList(), emptyList(), emptyList(), LogbookContent(emptyList()))
                val presenter = presenter()
                presenter.choose {}

                // when
                presenter.next()

                // then
                assertThat(reading.captured.flights).hasSize(2)
                assertThat(presenter.stage).isEqualTo(SpreadsheetStage.Wings)
            }
    }

    @Nested
    inner class ChoosingWings {
        @Test
        fun `counts the rows already in the logbook on the chosen wing and leaves them out`() =
            runTest {
                // given
                val first = aFlight(2)
                val second = aFlight(3)
                val logged = Flight(FlightId("logged"), first.toDetails(BIRDY), Instant.parse("2026-09-20T17:00:00Z"))
                val group = WingGroup(first.wing, listOf(first, second), BIRDY)
                val presenter = presenterOnWings(group, logbook = listOf(logged))

                // when
                val present = presenter.alreadyPresent

                // then
                assertThat(present).containsExactly(first)
                assertThat(presenter.importableCount).isEqualTo(1)
            }

        @Test
        fun `finds nothing already present for a wing that is new`() =
            runTest {
                // given
                val row = aFlight(2)
                val logged = Flight(FlightId("logged"), row.toDetails(BIRDY), Instant.parse("2026-09-20T17:00:00Z"))
                val presenter = presenterOnWings(WingGroup(row.wing, listOf(row), BIRDY), logbook = listOf(logged))

                // when
                val group = presenter.draft!!.groups.single()
                presenter.mapping.createWingFor(group, NewWingSpec("Birdy", "Niviuk", "Birdy"))

                // then
                assertThat(presenter.alreadyPresent).isEmpty()
                assertThat(presenter.importableCount).isEqualTo(1)
            }

        @Test
        fun `imports the rows of every wing that is not skipped`() =
            runTest {
                // given
                val birdy = aFlight(2)
                val alpha = aFlight(3, "Alpha")
                val presenter =
                    presenterOnWings(
                        WingGroup(birdy.wing, listOf(birdy), BIRDY),
                        WingGroup(alpha.wing, listOf(alpha), null),
                    )
                presenter.mapping.choose(presenter.draft!!.groups[1], WingChoice.Skip)
                val selection = slot<List<Pair<SpreadsheetFlight, WingTarget>>>()
                coEvery { import(capture(selection), any()) } returns ImportResult(1, emptyList(), 0, emptyList())

                // when
                presenter.import {}

                // then
                assertThat(selection.captured).containsExactly(birdy to WingTarget.Existing(BIRDY))
                assertThat(presenter.stage).isEqualTo(SpreadsheetStage.Done)
            }

        @Test
        fun `goes back to the wings with a message when importing fails`() =
            runTest {
                // given
                val row = aFlight(2)
                val presenter = presenterOnWings(WingGroup(row.wing, listOf(row), BIRDY))
                coEvery { import(any(), any()) } throws IOException("Disk full")

                // when
                presenter.import {}

                // then
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.ImportSpreadsheet)
                assertThat(presenter.stage).isEqualTo(SpreadsheetStage.Wings)
            }
    }

    @Nested
    inner class ColumnLetter {
        @Test
        fun `names columns as spreadsheets do`() {
            assertThat(listOf(0, 25, 26, 27, 701, 702).map(::columnLetter))
                .containsExactly("A", "Z", "AA", "AB", "ZZ", "AAA")
        }
    }
}
