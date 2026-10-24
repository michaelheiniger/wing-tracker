package ch.qscqlmpa.wingtracker.flights.spreadsheet

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.importing.FlightImporter
import ch.qscqlmpa.wingtracker.flights.importing.ImportResult
import ch.qscqlmpa.wingtracker.flights.importing.NewWingSpec
import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import ch.qscqlmpa.wingtracker.flights.importing.WingGroup
import ch.qscqlmpa.wingtracker.flights.importing.WingTarget
import ch.qscqlmpa.wingtracker.flights.importing.suggestWing
import ch.qscqlmpa.wingtracker.gear.wing.RegisterWing
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A sheet of the file with the columns its header suggests. */
data class SheetChoice(
    val sheet: Sheet,
    val suggested: ColumnMapping,
)

data class OpenedSpreadsheet(
    val sheets: List<SheetChoice>,
    // The sheet that looks like a logbook, so a file with a summary sheet first still opens on the flights.
    val preferred: Int,
)

/** Reads the file and guesses, for every sheet, which column holds what. */
class OpenSpreadsheet(
    private val reader: SpreadsheetReader,
) {
    /** @throws SpreadsheetException when the file is not a readable spreadsheet. */
    suspend operator fun invoke(bytes: ByteArray): OpenedSpreadsheet {
        val sheets =
            reader.read(bytes).map { sheet ->
                SheetChoice(sheet, ColumnMapping.suggest(readSheet(sheet, ColumnMapping()).headers))
            }
        val preferred = sheets.indexOfFirst { it.suggested.isComplete }.coerceAtLeast(0)
        return OpenedSpreadsheet(sheets, preferred)
    }
}

data class SpreadsheetImportDraft(
    val groups: List<WingGroup<SpreadsheetFlight>>,
    val problems: List<RowProblem>,
    val existingWings: List<Wing>,
    val logbook: LogbookContent,
)

/** Groups the flights of the sheet by the wing named in it, so the pilot only has to decide about the wings. */
class PrepareSpreadsheetImport(
    private val flights: FlightRepository,
    private val wings: WingRepository,
) {
    suspend operator fun invoke(reading: SheetReading): SpreadsheetImportDraft {
        val existing = wings.findAll().items.sortedBy { it.details.name.lowercase() }
        val groups =
            reading.flights
                // Typed by hand, the same wing is often spelled with different case or spacing.
                .groupBy {
                    it.wing.name
                        .trim()
                        .lowercase()
                }.values
                .map { list ->
                    val wing =
                        SourceWing(
                            list
                                .first()
                                .wing.name
                                .trim(),
                            brand = null,
                        )
                    WingGroup(wing, list.map { it.copy(wing = wing) }, suggestWing(wing, existing))
                }.sortedByDescending { it.lastDate }
        return SpreadsheetImportDraft(groups, reading.problems, existing, LogbookContent(flights.findAll().items))
    }
}

/**
 * Brings the chosen rows in, creating the wings that were asked for. Rows already in the logbook are left out, so
 * importing the same file again, or a longer version of it, adds only what is new.
 */
@OptIn(ExperimentalUuidApi::class)
class ImportSpreadsheetFlights(
    private val flights: FlightRepository,
    wings: WingRepository,
    clock: AppClock,
    registerWing: RegisterWing,
    private val transactions: LogbookTransactions,
    // Injectable so tests get deterministic ids.
    newId: () -> FlightId = { FlightId(Uuid.random().toString()) },
) {
    private val importer = FlightImporter(flights, wings, clock, registerWing, newId)

    suspend operator fun invoke(
        selection: List<Pair<SpreadsheetFlight, WingTarget>>,
        newWings: Map<String, NewWingSpec>,
    ): ImportResult<SpreadsheetFlight> =
        transactions.run(LogbookChange.FlightsImportedFromSpreadsheet) {
            val chosen = selection.filter { it.second != WingTarget.Skip }
            val onExisting =
                chosen.mapNotNull { (flight, target) ->
                    (target as? WingTarget.Existing)?.let {
                        flight to
                            it.wingId
                    }
                }
            val present = LogbookContent(flights.findAll().items).alreadyPresent(onExisting)
            // In the order of the sheet within a day, which is the order the pilot wrote them down.
            val ordered =
                chosen.filter { it.first !in present }.sortedWith(
                    compareBy({ it.first.date }, { it.first.rowNumber }),
                )
            importer.import(ordered, newWings, skipped = selection.size - ordered.size)
        }
}
