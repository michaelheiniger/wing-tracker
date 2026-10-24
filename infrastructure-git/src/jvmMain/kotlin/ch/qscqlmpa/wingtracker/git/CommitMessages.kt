package ch.qscqlmpa.wingtracker.git

import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.sync.LogbookChange

// Read in the Git history rather than in the app, so they are not translated (ADR 0014, ADR 0033).
internal fun LogbookChange.commitMessage(interrupted: Boolean = false): String {
    val subject = subject()
    return if (interrupted) "$subject (interrupted)" else subject
}

internal const val FIRST_COMMIT_MESSAGE = "Start synchronising the logbook"
internal const val STRAY_CHANGES_MESSAGE = "Changes made outside the app"

@Suppress("CyclomaticComplexMethod")
private fun LogbookChange.subject(): String =
    when (this) {
        is LogbookChange.GearRegistered -> "Register ${gear.described()}"
        is LogbookChange.GearDetailsUpdated -> "Update ${gear.described()}"
        is LogbookChange.GearRetired -> "Retire ${gear.described()}"
        is LogbookChange.GearDeleted -> "Delete ${gear.described()}"
        is LogbookChange.CheckRecorded -> "Record check ${check.value} on ${gear.described()}"
        is LogbookChange.CheckUpdated -> "Update check ${check.value} on ${gear.described()}"
        is LogbookChange.CheckDeleted -> "Delete check ${check.value} on ${gear.described()}"
        is LogbookChange.CheckPlanned -> "Plan check ${plannedCheck.value} on ${gear.described()}"
        is LogbookChange.PlannedCheckUpdated -> "Update planned check ${plannedCheck.value} on ${gear.described()}"
        is LogbookChange.PlannedCheckCancelled -> "Cancel planned check ${plannedCheck.value} on ${gear.described()}"
        is LogbookChange.FlightLogged -> "Log flight ${flight.value}"
        is LogbookChange.FlightUpdated -> "Update flight ${flight.value}"
        is LogbookChange.FlightDeleted -> "Delete flight ${flight.value}"
        is LogbookChange.FlightImportedFromTrack -> "Import flight ${flight.value} from a track"
        is LogbookChange.TrackAttached -> "Attach a track to flight ${flight.value}"
        is LogbookChange.FlightExportForgotten -> "Forget the export of flight ${flight.value} to $system"
        is LogbookChange.FlightExportRestored -> "Restore the export of flight ${flight.value} to $system"
        LogbookChange.FlightsImportedFromSyride -> "Import flights from Syride"
        LogbookChange.FlightsImportedFromSpreadsheet -> "Import flights from a spreadsheet"
        LogbookChange.FlightsExportedToAirtime -> "Export flights to air-time"
        LogbookChange.SyrideFlightsCompleted -> "Complete flights from Syride"
        LogbookChange.SyrideTakeOffTimesUpdated -> "Update take-off times from Syride"
        LogbookChange.ThemeModeChanged -> "Change the theme"
        LogbookChange.ReminderLeadTimesChanged -> "Change the reminder lead times"
        LogbookChange.SyrideUsernameChanged -> "Change the Syride account"
        LogbookChange.AirtimeNickChanged -> "Change the air-time pilot"
        LogbookChange.CloudStorageChanged -> "Change the cloud storage for backups"
    }

private fun GearRef.described() = "${kind.name.lowercase()} $id"
