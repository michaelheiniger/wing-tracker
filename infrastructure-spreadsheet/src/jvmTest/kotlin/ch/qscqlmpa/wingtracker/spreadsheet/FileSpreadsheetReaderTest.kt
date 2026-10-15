package ch.qscqlmpa.wingtracker.spreadsheet

import ch.qscqlmpa.wingtracker.flights.spreadsheet.Cell
import ch.qscqlmpa.wingtracker.flights.spreadsheet.Sheet
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetException
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetProblem
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.minutes

private fun text(value: String) = Cell.Text(value)

private fun number(value: Double) = Cell.Number(value)

private val HEADERS =
    listOf(
        "Flight no.",
        "Date",
        "Glider",
        "Takeoff",
        "Landing",
        "Duration (h)",
        "Duration (min)",
        "Distance (km)",
        "Remarks / Observations",
    ).map(::text)

/** What both sample files hold: a summary sheet, then the flights with typed cells, a formula and empty rows. */
private val SAMPLE =
    listOf(
        Sheet("Summary", listOf(listOf(text("Paragliding logbook – summary")))),
        Sheet(
            "Flights",
            listOf(
                HEADERS,
                listOf(
                    number(1.0),
                    Cell.Date(LocalDate(2023, 5, 20)),
                    text("Alpha"),
                    text("Sonchaux"),
                    text("Rennaz"),
                    Cell.Empty,
                    number(20.0),
                    Cell.Empty,
                    text("First  flight  !"),
                ),
                listOf(
                    number(2.0),
                    Cell.Date(LocalDate(2023, 6, 2)),
                    text("Birdy"),
                    text("Curnaux"),
                    text("Villy"),
                    number(1.0),
                    number(30.0),
                    number(4.5),
                    text("Thermals\n1er déco sud-est"),
                ),
                emptyList(),
                emptyList(),
                listOf(
                    number(3.0),
                    text("30.12.2023"),
                    text("Birdy"),
                    text("Sonchaux"),
                    text("Rennaz"),
                    Cell.Empty,
                    Cell.Time(75.minutes),
                ),
            ),
        ),
    )

private fun resource(name: String): ByteArray =
    checkNotNull(FileSpreadsheetReaderTest::class.java.getResourceAsStream("/$name")) { name }.readBytes()

private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

private fun odsOf(table: String) =
    zipOf(
        "mimetype" to "application/vnd.oasis.opendocument.spreadsheet",
        "content.xml" to
            """
            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
              <office:body><office:spreadsheet>$table</office:spreadsheet></office:body>
            </office:document-content>
            """.trimIndent(),
    )

class FileSpreadsheetReaderTest {
    private val reader = FileSpreadsheetReader()

    @Nested
    inner class Read {
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = ["sample-logbook.ods", "sample-logbook.xlsx"])
        fun `reads every sheet with its typed cells, whatever the format`(file: String) =
            runTest {
                // when
                val sheets = reader.read(resource(file))

                // then
                assertThat(sheets).isEqualTo(SAMPLE)
            }

        @Test
        fun `does not expand the empty rows and columns a sheet repeats up to its full size`() =
            runTest {
                // given
                val bytes =
                    odsOf(
                        """
                        <table:table table:name="Flights">
                          <table:table-row>
                            <table:table-cell office:value-type="string"><text:p>Date</text:p></table:table-cell>
                            <table:table-cell table:number-columns-repeated="16383"/>
                          </table:table-row>
                          <table:table-row table:number-rows-repeated="1048575">
                            <table:table-cell table:number-columns-repeated="16384"/>
                          </table:table-row>
                        </table:table>
                        """.trimIndent(),
                    )

                // when
                val sheets = reader.read(bytes)

                // then
                assertThat(sheets).containsExactly(Sheet("Flights", listOf(listOf(text("Date")))))
            }

        @Test
        fun `says an Excel 97-2003 file is in the old format`() =
            runTest {
                // given
                val xls = listOf(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1, 0x00).map { it.toByte() }.toByteArray()

                // when / then
                assertThat(runCatching { reader.read(xls) }.exceptionOrNull())
                    .isInstanceOfSatisfying(SpreadsheetException::class.java) {
                        assertThat(it.problem).isEqualTo(SpreadsheetProblem.OldExcelFormat)
                    }
            }

        @Test
        fun `says a file that is no spreadsheet cannot be read`() =
            runTest {
                // given
                val notASpreadsheet = "Date;Glider\n02.06.2023;Birdy".toByteArray()

                // when / then
                assertThat(runCatching { reader.read(notASpreadsheet) }.exceptionOrNull())
                    .isInstanceOfSatisfying(SpreadsheetException::class.java) {
                        assertThat(it.problem).isEqualTo(SpreadsheetProblem.Unreadable)
                    }
            }

        @Test
        fun `says a damaged spreadsheet cannot be read`() =
            runTest {
                // given
                val damaged = odsOf("<table:table table:name=\"Flights\"><table:table-row>")

                // when / then
                assertThat(runCatching { reader.read(damaged) }.exceptionOrNull())
                    .isInstanceOfSatisfying(SpreadsheetException::class.java) {
                        assertThat(it.problem).isEqualTo(SpreadsheetProblem.Unreadable)
                    }
            }
    }
}
