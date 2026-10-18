package ch.qscqlmpa.wingtracker.flights.airtime

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.IgcFile
import ch.qscqlmpa.wingtracker.flights.igc.InvalidTrackException
import ch.qscqlmpa.wingtracker.flights.igc.TrackReader
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

/**
 * A flight that can be sent, with either the air-time glider of its wing ([gliderId]) or the wing of
 * [AirtimeExportDraft.newGliders] whose glider has to be created first ([newGlider]).
 */
data class AirtimeExportCandidate(
    val flight: Flight,
    val wing: Wing,
    val gliderId: String?,
    // air-time's site of the same name; only manual entries carry one.
    val takeOffId: Long?,
    val newGlider: WingId? = null,
    // When air-time will see the flight start; it finds the flight's id there once sent. Null when unknown.
    val start: Instant? = null,
) {
    // Sent as its stored track when it has one, otherwise as a manual entry.
    val withTrack: Boolean get() = flight.igcFile != null
}

enum class AirtimeSkipReason {
    // A flight starting in the same minute is already on air-time, e.g. from air-time's own Syride import.
    AlreadyOnAirtime,

    // Without a track nor a take-off time there is no exact start, which air-time requires.
    NoTakeOffTime,
    WingNotFound,
}

data class SkippedAirtimeFlight(
    val flight: Flight,
    val reason: AirtimeSkipReason,
)

/** What an export would do, for the pilot to review. Lists are oldest first. */
data class AirtimeExportDraft(
    val nick: String,
    val candidates: List<AirtimeExportCandidate>,
    // One wing per glider to create; the pilot gives each one's colour.
    val newGliders: List<Wing>,
    val skipped: List<SkippedAirtimeFlight>,
    // Flights an earlier export already sent.
    val alreadyExported: Int,
)

/** Reads the pilot's air-time account and works out which flights to send and how (ADR 0029). */
class PrepareAirtimeExport(
    private val account: AirtimeAccount,
    private val flights: FlightRepository,
    private val wings: WingRepository,
    private val attachments: AttachmentStore,
    private val trackReader: TrackReader,
    private val clock: AppClock,
) {
    suspend operator fun invoke(
        nick: String,
        token: String,
    ): AirtimeExportDraft {
        val gliders = account.gliders(token).associateBy { GliderKey.of(it) }
        val startsOnAirtime = account.flightStarts(nick.trim()).map { it.minute() }.toSet()
        val wingsById = wings.findAll().items.associateBy { it.id }
        val (exported, pending) =
            flights
                .findAll()
                .items
                .sortedWith(compareBy({ it.details.date }, { it.details.takeOffTime }, { it.loggedAt }))
                .partition { it.isExportedTo(AIRTIME_SYSTEM) }

        val candidates = mutableListOf<AirtimeExportCandidate>()
        val skipped = mutableListOf<SkippedAirtimeFlight>()
        for (flight in pending) {
            val wing = wingsById[flight.details.wingId]
            val start = startOf(flight)
            val reason =
                when {
                    wing == null -> AirtimeSkipReason.WingNotFound
                    flight.igcFile == null && flight.details.takeOffTime == null -> AirtimeSkipReason.NoTakeOffTime
                    start != null && start.minute() in startsOnAirtime -> AirtimeSkipReason.AlreadyOnAirtime
                    else -> null
                }
            if (reason != null) {
                skipped += SkippedAirtimeFlight(flight, reason)
            } else {
                candidates +=
                    AirtimeExportCandidate(
                        flight,
                        checkNotNull(wing),
                        gliders[GliderKey.of(wing)]?.id,
                        null,
                        start = start,
                    )
            }
        }
        // Wings that share brand, model and size share one glider, which is created once.
        val newGliders = candidates.filter { it.gliderId == null }.map { it.wing }.distinctBy { GliderKey.of(it) }
        val byKey = newGliders.associateBy({ GliderKey.of(it) }, { it.id })
        val complete =
            withTakeOffIds(candidates).map {
                if (it.gliderId == null) it.copy(newGlider = byKey.getValue(GliderKey.of(it.wing))) else it
            }
        return AirtimeExportDraft(nick.trim(), complete, newGliders, skipped, exported.size)
    }

    /**
     * Where air-time starts the flight: at the first fix of a track it is sent, which can be minutes before the
     * take-off detected in it; otherwise at the take-off time. Null when neither is known.
     */
    private suspend fun startOf(flight: Flight): Instant? =
        flight.igcFile?.let { firstFixOf(it) }
            ?: flight.details.takeOffTime?.let { clock.instant(LocalDateTime(flight.details.date, it)) }

    private suspend fun firstFixOf(igc: IgcFile): Instant? =
        try {
            trackReader
                .read(attachments.read(igc.attachment.id))
                .track.fixes
                .firstOrNull()
                ?.time
        } catch (_: InvalidTrackException) {
            // air-time reads the track itself; an unreadable one only means it cannot be compared.
            null
        }

    /** air-time's take-off site of the same name, for manual entries only: a track tells air-time where it was. */
    private suspend fun withTakeOffIds(candidates: List<AirtimeExportCandidate>): List<AirtimeExportCandidate> {
        if (candidates.all { it.withTrack || it.flight.details.takeOffSite.shown == null }) {
            return candidates
        }
        val byName = account.takeOffs().associateBy({ it.name.trim().lowercase() }, { it.id })
        return candidates.map { candidate ->
            val site = candidate.flight.details.takeOffSite.shown
            if (candidate.withTrack || site == null) {
                candidate
            } else {
                candidate.copy(takeOffId = byName[site.trim().lowercase()])
            }
        }
    }
}

/** How a wing and an air-time glider are recognised as the same, every time (ADR 0029). */
internal data class GliderKey(
    val brand: String,
    val model: String,
    val size: String,
) {
    companion object {
        fun of(glider: AirtimeGlider) = key(glider.brand, glider.model, glider.size)

        fun of(wing: Wing) = key(wing.details.manufacturer, wing.details.model, wing.details.size.orEmpty())

        private fun key(
            brand: String,
            model: String,
            size: String,
        ) = GliderKey(brand.trim().lowercase(), model.trim().lowercase(), size.trim().lowercase())
    }
}

// air-time keeps seconds, the logbook only minutes, so starts are compared to the minute.
internal fun Instant.minute(): Long = epochSeconds.floorDiv(SECONDS_PER_MINUTE)

internal const val SECONDS_PER_MINUTE = 60L
