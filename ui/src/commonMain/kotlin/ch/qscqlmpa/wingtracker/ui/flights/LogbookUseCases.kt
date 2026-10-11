package ch.qscqlmpa.wingtracker.ui.flights

import ch.qscqlmpa.wingtracker.flights.DeleteFlight
import ch.qscqlmpa.wingtracker.flights.GetLogbook
import ch.qscqlmpa.wingtracker.flights.LogFlight
import ch.qscqlmpa.wingtracker.flights.UpdateFlight
import ch.qscqlmpa.wingtracker.flights.igc.ImportIgcFlight
import ch.qscqlmpa.wingtracker.flights.igc.PrepareIgcImport
import ch.qscqlmpa.wingtracker.gear.maintenance.ReadAttachment

/** Use cases the logbook section needs; passed in so this module never sees the DI framework. */
class LogbookUseCases(
    val getLogbook: GetLogbook,
    val logFlight: LogFlight,
    val updateFlight: UpdateFlight,
    val deleteFlight: DeleteFlight,
    val prepareIgcImport: PrepareIgcImport,
    val importIgcFlight: ImportIgcFlight,
    val readAttachment: ReadAttachment,
)
