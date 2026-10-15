package ch.qscqlmpa.wingtracker.ui.flights

import ch.qscqlmpa.wingtracker.flights.DeleteFlight
import ch.qscqlmpa.wingtracker.flights.GetLogbook
import ch.qscqlmpa.wingtracker.flights.LogFlight
import ch.qscqlmpa.wingtracker.flights.UpdateFlight
import ch.qscqlmpa.wingtracker.flights.igc.ImportIgcFlight
import ch.qscqlmpa.wingtracker.flights.igc.PrepareIgcImport
import ch.qscqlmpa.wingtracker.flights.spreadsheet.ImportSpreadsheetFlights
import ch.qscqlmpa.wingtracker.flights.spreadsheet.OpenSpreadsheet
import ch.qscqlmpa.wingtracker.flights.spreadsheet.PrepareSpreadsheetImport
import ch.qscqlmpa.wingtracker.flights.syride.ImportSyrideFlights
import ch.qscqlmpa.wingtracker.flights.syride.PrepareSyrideImport
import ch.qscqlmpa.wingtracker.gear.maintenance.ReadAttachment
import ch.qscqlmpa.wingtracker.settings.GetAppSettings
import ch.qscqlmpa.wingtracker.settings.SaveDeviceFolder
import ch.qscqlmpa.wingtracker.settings.SaveSyrideUsername

/** Use cases the logbook section needs; passed in so this module never sees the DI framework. */
class LogbookUseCases(
    val getLogbook: GetLogbook,
    val logFlight: LogFlight,
    val updateFlight: UpdateFlight,
    val deleteFlight: DeleteFlight,
    val prepareIgcImport: PrepareIgcImport,
    val importIgcFlight: ImportIgcFlight,
    val readAttachment: ReadAttachment,
    val prepareSyrideImport: PrepareSyrideImport,
    val importSyrideFlights: ImportSyrideFlights,
    val saveSyrideUsername: SaveSyrideUsername,
    val openSpreadsheet: OpenSpreadsheet,
    val prepareSpreadsheetImport: PrepareSpreadsheetImport,
    val importSpreadsheetFlights: ImportSpreadsheetFlights,
    val getAppSettings: GetAppSettings,
    val saveDeviceFolder: SaveDeviceFolder,
)
