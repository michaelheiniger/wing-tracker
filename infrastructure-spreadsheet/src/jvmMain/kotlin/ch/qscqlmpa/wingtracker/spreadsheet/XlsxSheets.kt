package ch.qscqlmpa.wingtracker.spreadsheet

import ch.qscqlmpa.wingtracker.flights.spreadsheet.Cell
import ch.qscqlmpa.wingtracker.flights.spreadsheet.Sheet
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.dhatim.fastexcel.reader.CellType
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.ReadingOptions
import org.dhatim.fastexcel.reader.Row
import java.io.InputStream
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds
import org.dhatim.fastexcel.reader.Cell as XlsxCell

/**
 * Reads an Office Open XML spreadsheet (what Excel saves). Excel stores dates and times as numbers and tells them
 * apart only by their display format, so the format decides which kind of cell a number becomes.
 */
internal object XlsxSheets {
    fun read(content: InputStream): List<Sheet> =
        ReadableWorkbook(content, ReadingOptions(true, false)).use { workbook ->
            val epoch = if (workbook.isDate1904) EPOCH_1904 else EPOCH_1900
            workbook.sheets.toList().map { sheet ->
                // Rows without any cell are not in the file; they are put back so row numbers match what Excel shows.
                val byNumber = sheet.read().associateBy { it.rowNum }
                val last = byNumber.keys.maxOrNull() ?: 0
                Sheet(sheet.name, (1..last).map { number -> byNumber[number]?.let { cellsOf(it, epoch) }.orEmpty() })
            }
        }

    private fun cellsOf(
        row: Row,
        epoch: LocalDate,
    ): List<Cell> {
        val cells = (0 until row.cellCount).map { index -> row.getCell(index)?.let { cellOf(it, epoch) } ?: Cell.Empty }
        return cells.dropLastWhile { it == Cell.Empty }
    }

    private fun cellOf(
        cell: XlsxCell,
        epoch: LocalDate,
    ): Cell =
        when (cell.type) {
            CellType.NUMBER -> number(cell.rawValue.toDouble(), cell.dataFormatId, cell.dataFormatString, epoch)

            // A formula is read for the value it last showed; Excel saves that along with the formula.
            CellType.FORMULA -> formula(cell, epoch)

            CellType.STRING, CellType.BOOLEAN -> cell.text.ifBlank { null }?.let(Cell::Text) ?: Cell.Empty

            CellType.ERROR, CellType.EMPTY, null -> Cell.Empty
        }

    private fun formula(
        cell: XlsxCell,
        epoch: LocalDate,
    ): Cell {
        val raw = cell.rawValue ?: return Cell.Empty
        return raw.toDoubleOrNull()?.let { number(it, cell.dataFormatId, cell.dataFormatString, epoch) }
            ?: raw.ifBlank { null }?.let(Cell::Text)
            ?: Cell.Empty
    }

    private fun number(
        value: Double,
        formatId: Int?,
        format: String?,
        epoch: LocalDate,
    ): Cell =
        when (kindOf(formatId, format)) {
            NumberKind.Date -> Cell.Date(epoch.plus(floor(value).toInt(), DateTimeUnit.DAY))
            NumberKind.Time -> Cell.Time((value * SECONDS_PER_DAY).roundToLong().seconds)
            NumberKind.Plain -> Cell.Number(value)
        }

    private enum class NumberKind { Date, Time, Plain }

    private fun kindOf(
        formatId: Int?,
        format: String?,
    ): NumberKind {
        if (formatId in BUILT_IN_DATES) return NumberKind.Date
        if (formatId in BUILT_IN_TIMES) return NumberKind.Time
        // Quoted text and colours or conditions in brackets are not about the value; elapsed hours ([h]) are.
        val code =
            format
                .orEmpty()
                .lowercase()
                .replace(QUOTED, "")
                .replace(BRACKETS, "")
        return when {
            code.any { it == 'y' || it == 'd' } -> NumberKind.Date
            code.any { it == 'h' || it == 's' } || format.orEmpty().contains("[h", ignoreCase = true) -> NumberKind.Time
            else -> NumberKind.Plain
        }
    }

    // Excel counts 1900 as a leap year, so its day 0 is the last day of 1899 and day 60 does not exist; counting from
    // the day before keeps every date from March 1900 on right, which is all a logbook needs.
    private val EPOCH_1900 = LocalDate(1899, 12, 30)
    private val EPOCH_1904 = LocalDate(1904, 1, 1)
    private const val SECONDS_PER_DAY = 86_400.0
    private val BUILT_IN_DATES = (14..17).toSet() + 22
    private val BUILT_IN_TIMES = (18..21).toSet() + (45..47)
    private val QUOTED = Regex("\"[^\"]*\"")
    private val BRACKETS = Regex("""\[[^\]]*]""")
}
