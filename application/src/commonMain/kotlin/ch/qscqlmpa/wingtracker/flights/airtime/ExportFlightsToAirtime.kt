package ch.qscqlmpa.wingtracker.flights.airtime

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
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
    /**
     * [colours] holds the colour the pilot gave for each wing of [AirtimeExportDraft.newGliders]; [onProgress] hears
     * how many of the chosen flights are done, since sending many takes a while.
     */
    suspend operator fun invoke(
        token: String,
        draft: AirtimeExportDraft,
        chosen: Set<FlightId>,
        colours: Map<WingId, String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): AirtimeExportResult {
        val selection = draft.candidates.filter { it.flight.id in chosen }
        // Only the gliders the chosen flights need; the others stay uncreated.
        val missing = selection.mapNotNull { it.newGlider }.toSet()
        val gliderIds = mutableMapOf<WingId, String>()
        var exported = 0
        try {
            for (wing in draft.newGliders.filter { it.id in missing }) {
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
                gliderIds[wing.id] = glider.id
            }
            for ((index, candidate) in selection.withIndex()) {
                val gliderId = candidate.gliderId ?: gliderIds.getValue(checkNotNull(candidate.newGlider))
                // Read again, so a flight edited or exported since the preview is neither sent twice nor overwritten.
                val flight = flights.findById(candidate.flight.id)?.takeUnless { it.isExportedTo(AIRTIME_SYSTEM) }
                if (flight != null) {
                    send(token, flight, gliderId, candidate.takeOffId)
                    val airtimeId = idOnAirtime(draft.nick, candidate.start)
                    flights.save(flight.markExported(AIRTIME_SYSTEM, clock.now(), airtimeId?.toString()))
                    exported++
                }
                onProgress(index + 1, selection.size)
            }
        } catch (e: AirtimeException) {
            return AirtimeExportResult(exported, gliderIds.size, e.problem)
        }
        return AirtimeExportResult(exported, gliderIds.size)
    }

    /**
     * air-time answers a new flight with no id, so it is looked up among the flights starting in the same minute
     * (ADR 0029). Should there be several, the newest is the one just sent.
     */
    private suspend fun idOnAirtime(
        nick: String,
        start: Instant?,
    ): Long? {
        if (start == null) return null
        val from = Instant.fromEpochSeconds(start.minute() * SECONDS_PER_MINUTE)
        return try {
            account
                .flightsStartingBetween(nick, from, from + LAST_SECOND_OF_MINUTE)
                .filter { it.start.minute() == start.minute() }
                .maxOfOrNull { it.id }
        } catch (_: AirtimeException) {
            // The flight is on air-time already; without its id it is still marked, so it is never sent twice.
            null
        }
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

private val LAST_SECOND_OF_MINUTE = 59.seconds
