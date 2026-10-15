package ch.qscqlmpa.wingtracker.flights.spreadsheet

import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/** A cell as the spreadsheet typed it; text that only looks like a date or a number stays text. */
sealed interface Cell {
    data object Empty : Cell

    data class Text(
        val value: String,
    ) : Cell

    data class Number(
        val value: Double,
    ) : Cell

    data class Date(
        val value: LocalDate,
    ) : Cell

    // A cell formatted as a time; logbooks use it for how long a flight lasted.
    data class Time(
        val value: Duration,
    ) : Cell
}

/** [rows] are as in the file, empty ones included, so a row's position is its number as the pilot sees it. */
data class Sheet(
    val name: String,
    val rows: List<List<Cell>>,
)

enum class SpreadsheetProblem {
    // Neither an OpenDocument nor an Office Open XML spreadsheet, or a damaged one.
    Unreadable,

    // The binary format of Excel 97-2003, which is not read; the pilot can save the file again in a newer format.
    OldExcelFormat,
}

class SpreadsheetException(
    val problem: SpreadsheetProblem,
    detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail ?: problem.name, cause)

/** Port to whatever reads spreadsheet files, so the rest of the application never sees a file format (ADR 0022). */
interface SpreadsheetReader {
    /** @throws SpreadsheetException when the bytes are not a readable spreadsheet. */
    suspend fun read(bytes: ByteArray): List<Sheet>
}
