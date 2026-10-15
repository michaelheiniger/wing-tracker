package ch.qscqlmpa.wingtracker.ui.spreadsheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.importing.ImportResult
import ch.qscqlmpa.wingtracker.flights.importing.WingGroup
import ch.qscqlmpa.wingtracker.flights.spreadsheet.ColumnMapping
import ch.qscqlmpa.wingtracker.flights.spreadsheet.FlightField
import ch.qscqlmpa.wingtracker.flights.spreadsheet.ImportSpreadsheetFlights
import ch.qscqlmpa.wingtracker.flights.spreadsheet.OpenSpreadsheet
import ch.qscqlmpa.wingtracker.flights.spreadsheet.OpenedSpreadsheet
import ch.qscqlmpa.wingtracker.flights.spreadsheet.PrepareSpreadsheetImport
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SheetReading
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetException
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetFlight
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetImportDraft
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetProblem
import ch.qscqlmpa.wingtracker.flights.spreadsheet.readSheet
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.flights.importing.WingChoice
import ch.qscqlmpa.wingtracker.ui.flights.importing.WingMapping
import ch.qscqlmpa.wingtracker.ui.platform.FilePicker
import ch.qscqlmpa.wingtracker.ui.toFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Where the import is: choose and read the file, say which column is what, decide about the wings, or the result. */
enum class SpreadsheetStage { File, Columns, Wings, Importing, Done }

/**
 * Drives the import from a spreadsheet: read the file, let the pilot confirm which column holds what while seeing
 * the flights it gives, then decide about the wings as in every import. Nothing is saved before the last step.
 */
class SpreadsheetImportPresenter(
    private val scope: CoroutineScope,
    private val filePicker: FilePicker,
    private val pickTitle: String,
    private val open: OpenSpreadsheet,
    private val prepare: PrepareSpreadsheetImport,
    private val import: ImportSpreadsheetFlights,
) {
    var stage by mutableStateOf(SpreadsheetStage.File)
        private set

    // Null while the file is being chosen or read.
    var opened by mutableStateOf<OpenedSpreadsheet?>(null)
        private set
    var sheetIndex by mutableStateOf(0)
        private set
    var columns by mutableStateOf(ColumnMapping())
        private set
    var reading by mutableStateOf<SheetReading?>(null)
        private set
    var draft by mutableStateOf<SpreadsheetImportDraft?>(null)
        private set
    val mapping = WingMapping()
    var result by mutableStateOf<ImportResult<SpreadsheetFlight>?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set

    val canContinue: Boolean get() = columns.isComplete && reading?.flights.orEmpty().isNotEmpty()

    /** Lets the pilot choose a file and reads it; [onCancelled] when no file was chosen. */
    fun choose(onCancelled: () -> Unit) {
        stage = SpreadsheetStage.File
        failure = null
        opened = null
        scope.launch {
            try {
                val file = filePicker.pick(pickTitle, SPREADSHEET_EXTENSIONS).firstOrNull()
                if (file == null) {
                    onCancelled()
                    return@launch
                }
                val read = open(file.bytes)
                opened = read
                selectSheet(read.preferred)
                stage = SpreadsheetStage.Columns
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpreadsheetException) {
                failure = Failure(e.problem.failureKind(), e.message)
                // Any failure must reach the user as a message; it must never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = e.toFailure(FailureKind.SpreadsheetUnreadable)
            }
        }
    }

    fun selectSheet(index: Int) {
        val sheets = opened?.sheets ?: return
        sheetIndex = index
        update(sheets[index].suggested)
    }

    fun assign(
        field: FlightField,
        column: Int?,
    ) {
        update(columns.with(field, column))
    }

    fun next() {
        val current = reading ?: return
        failure = null
        scope.launch {
            try {
                val prepared = prepare(current)
                draft = prepared
                mapping.start(prepared.groups)
                stage = SpreadsheetStage.Wings
            } catch (e: CancellationException) {
                throw e
                // Any failure must reach the user as a message; it must never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = Failure(FailureKind.ImportSpreadsheet, e.message)
            }
        }
    }

    fun back() {
        stage = SpreadsheetStage.Columns
        failure = null
    }

    /** The rows already in the logbook on the wing they are going to, which the import leaves out. */
    val alreadyPresent: Set<SpreadsheetFlight>
        get() {
            val current = draft ?: return emptySet()
            val planned =
                current.groups.flatMap { group ->
                    val choice = mapping.choiceOf(group) as? WingChoice.Existing ?: return@flatMap emptyList()
                    group.flights.map { it to choice.wingId }
                }
            return current.logbook.alreadyPresent(planned)
        }

    /** The existing wing a group goes to, if any. */
    fun targetWing(group: WingGroup<SpreadsheetFlight>): Wing? {
        val choice = mapping.choiceOf(group) as? WingChoice.Existing ?: return null
        return draft?.existingWings?.firstOrNull { it.id == choice.wingId }
    }

    /** Flights that will be imported with the current choices; a new wing that is still incomplete holds back. */
    val importableCount: Int
        get() {
            val present = alreadyPresent
            return draft
                ?.groups
                .orEmpty()
                .filter { mapping.isReady(it) }
                .sumOf { group -> group.flights.count { it !in present } }
        }

    val allReady: Boolean
        get() = draft?.groups.orEmpty().all { mapping.choiceOf(it) == WingChoice.Skip || mapping.isReady(it) }

    fun import(onFinished: () -> Unit) {
        val current = draft ?: return
        stage = SpreadsheetStage.Importing
        scope.launch {
            try {
                result = import(mapping.selection(current.groups), mapping.newWings)
                stage = SpreadsheetStage.Done
                onFinished()
            } catch (e: CancellationException) {
                throw e
                // Any failure must reach the user as a message; it must never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = Failure(FailureKind.ImportSpreadsheet, e.message)
                stage = SpreadsheetStage.Wings
            }
        }
    }

    private fun update(newColumns: ColumnMapping) {
        columns = newColumns
        reading = opened?.sheets?.getOrNull(sheetIndex)?.let { readSheet(it.sheet, newColumns) }
    }
}

val SPREADSHEET_EXTENSIONS = setOf("ods", "xlsx")

private fun SpreadsheetProblem.failureKind() =
    when (this) {
        SpreadsheetProblem.Unreadable -> FailureKind.SpreadsheetUnreadable
        SpreadsheetProblem.OldExcelFormat -> FailureKind.SpreadsheetOldFormat
    }
