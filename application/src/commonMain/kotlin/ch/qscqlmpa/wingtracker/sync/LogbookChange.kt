package ch.qscqlmpa.wingtracker.sync

import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckId
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheckId

/**
 * What a transaction changes, one kind per use case, so the history can tell the pilot what happened. Only the
 * logbook's data appears here: device settings are never synchronised (ADR 0033).
 */
sealed interface LogbookChange {
    data class GearRegistered(
        val gear: GearRef,
    ) : LogbookChange

    data class GearDetailsUpdated(
        val gear: GearRef,
    ) : LogbookChange

    data class GearRetired(
        val gear: GearRef,
    ) : LogbookChange

    data class GearDeleted(
        val gear: GearRef,
    ) : LogbookChange

    data class CheckRecorded(
        val gear: GearRef,
        val check: CheckId,
        // The planned check this one carried out, if any.
        val completes: PlannedCheckId?,
    ) : LogbookChange

    data class CheckUpdated(
        val gear: GearRef,
        val check: CheckId,
    ) : LogbookChange

    data class CheckDeleted(
        val gear: GearRef,
        val check: CheckId,
    ) : LogbookChange

    data class CheckPlanned(
        val gear: GearRef,
        val plannedCheck: PlannedCheckId,
    ) : LogbookChange

    data class PlannedCheckUpdated(
        val gear: GearRef,
        val plannedCheck: PlannedCheckId,
    ) : LogbookChange

    data class PlannedCheckCancelled(
        val gear: GearRef,
        val plannedCheck: PlannedCheckId,
    ) : LogbookChange

    data class FlightLogged(
        val flight: FlightId,
    ) : LogbookChange

    data class FlightUpdated(
        val flight: FlightId,
    ) : LogbookChange

    data class FlightDeleted(
        val flight: FlightId,
    ) : LogbookChange

    data class FlightImportedFromTrack(
        val flight: FlightId,
    ) : LogbookChange

    data class TrackAttached(
        val flight: FlightId,
    ) : LogbookChange

    data class FlightExportForgotten(
        val flight: FlightId,
        val system: String,
    ) : LogbookChange

    data class FlightExportRestored(
        val flight: FlightId,
        val system: String,
    ) : LogbookChange

    data object FlightsImportedFromSyride : LogbookChange

    data object FlightsImportedFromSpreadsheet : LogbookChange

    data object FlightsExportedToAirtime : LogbookChange

    data object SyrideFlightsCompleted : LogbookChange

    data object SyrideTakeOffTimesUpdated : LogbookChange

    data object ThemeModeChanged : LogbookChange

    data object ReminderLeadTimesChanged : LogbookChange

    data object SyrideUsernameChanged : LogbookChange

    data object AirtimeNickChanged : LogbookChange

    data object CloudStorageChanged : LogbookChange
}
