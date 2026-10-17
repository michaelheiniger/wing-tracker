package ch.qscqlmpa.wingtracker.flights.airtime

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** [failure] is set when the export stopped early; the flights sent before it stay marked as exported. */
data class AirtimeExportResult(
    val exported: Int,
    val glidersCreated: Int,
    val failure: AirtimeProblem? = null,
)

/**
 * Sends the chosen flights of a draft to air-time, creating the missing gliders first. Each flight is marked as
 * exported as soon as air-time accepted it, so an interrupted export can simply be run again.
 */
@OptIn(ExperimentalUuidApi::class)
class ExportFlightsToAirtime(
    private val account: AirtimeAccount,
    private val flights: FlightRepository,
    private val attachments: AttachmentStore,
    private val clock: AppClock,
    // Injectable so tests get deterministic ids.
    private val newGliderId: () -> String = { Uuid.random().toString() },
) {
    /** [colours] holds the colour the pilot gave for each wing of [AirtimeExportDraft.newGliders]. */
    suspend operator fun invoke(
        token: String,
        draft: AirtimeExportDraft,
        chosen: Set<FlightId>,
        colours: Map<WingId, String>,
    ): AirtimeExportResult {
        val selection = draft.candidates.filter { it.flight.id in chosen }
        // Only the gliders the chosen flights need; the others stay uncreated.
        val missing = selection.filter { it.gliderId == null }.map { GliderKey.of(it.wing) }.toSet()
        val gliderIds = mutableMapOf<GliderKey, String>()
        var exported = 0
        try {
            for (wing in draft.newGliders.filter { GliderKey.of(it) in missing }) {
                val glider =
                    NewAirtimeGlider(
                        id = newGliderId(),
                        name = wing.details.name,
                        brand = wing.details.manufacturer,
                        model = wing.details.model,
                        size = wing.details.size.orEmpty(),
                        colour = requireNotNull(colours[wing.id]) { "No colour given for wing ${wing.id.value}" },
                    )
                account.createGlider(token, glider)
                gliderIds[GliderKey.of(wing)] = glider.id
            }
            for (candidate in selection) {
                val gliderId = candidate.gliderId ?: gliderIds.getValue(GliderKey.of(candidate.wing))
                // Read again, so a flight edited or exported since the preview is neither sent twice nor overwritten.
                val flight =
                    flights.findById(candidate.flight.id)?.takeUnless { it.isExportedTo(AIRTIME_SYSTEM) } ?: continue
                send(token, flight, gliderId, candidate.takeOffId)
                flights.save(flight.markExported(AIRTIME_SYSTEM, clock.now()))
                exported++
            }
        } catch (e: AirtimeException) {
            return AirtimeExportResult(exported, gliderIds.size, e.problem)
        }
        return AirtimeExportResult(exported, gliderIds.size)
    }

    private suspend fun send(
        token: String,
        flight: Flight,
        gliderId: String,
        takeOffId: Long?,
    ) {
        val igc = flight.igcFile
        if (igc != null) {
            account.uploadTrack(token, attachments.read(igc.attachment.id), igc.attachment.fileName, gliderId)
            return
        }
        val takeOffTime = checkNotNull(flight.details.takeOffTime) { "Flight ${flight.id.value} has no exact start" }
        val start = clock.instant(LocalDateTime(flight.details.date, takeOffTime))
        account.registerManualFlight(
            token,
            ManualAirtimeFlight(start, start + flight.details.duration, gliderId, takeOffId),
        )
    }
}
