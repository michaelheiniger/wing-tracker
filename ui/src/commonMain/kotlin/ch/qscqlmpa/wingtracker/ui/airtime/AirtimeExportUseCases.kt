package ch.qscqlmpa.wingtracker.ui.airtime

import ch.qscqlmpa.wingtracker.flights.airtime.ExportFlightsToAirtime
import ch.qscqlmpa.wingtracker.flights.airtime.GetAirtimePages
import ch.qscqlmpa.wingtracker.flights.airtime.PrepareAirtimeExport
import ch.qscqlmpa.wingtracker.settings.SaveAirtimeNick

/**
 * What the export to air-time needs; [token] lives as long as the application, so a token kept for the session
 * survives closing the dialog.
 */
class AirtimeExportUseCases(
    val prepare: PrepareAirtimeExport,
    val export: ExportFlightsToAirtime,
    val saveNick: SaveAirtimeNick,
    val pages: GetAirtimePages,
    val token: AirtimeToken,
)
