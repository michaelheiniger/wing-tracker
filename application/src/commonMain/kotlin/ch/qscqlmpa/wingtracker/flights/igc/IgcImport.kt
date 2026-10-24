package ch.qscqlmpa.wingtracker.flights.igc

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightHasTrackException
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightNotFoundException
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.FlightRules
import ch.qscqlmpa.wingtracker.flights.IgcFile
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.gear.maintenance.withStoredFiles
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What a track tells us about a flight, offered to the pilot to confirm or correct before anything is saved.
 * [duration] is null when the track never really moves, so the pilot enters it by hand.
 */
data class IgcImportDraft(
    val fileName: String,
    val checksum: String,
    val date: LocalDate,
    // Local and in whole minutes, as the logbook shows them and the flight records its take-off.
    val takeOffTime: LocalTime?,
    val landingTime: LocalTime?,
    val duration: Duration?,
    // Set when this exact file was imported before; the pilot decides whether to import it again.
    val duplicateOf: Flight?,
    // The flight already in the logbook this track most likely belongs to (ADR 0026).
    val match: TrackMatch? = null,
    // A track that matches nothing on a day with flights is likely a recording that was not a flight.
    val dayHasFlights: Boolean = false,
)

class PrepareIgcImport(
    private val reader: TrackReader,
    private val flights: FlightRepository,
    private val clock: AppClock,
) {
    private val matcher = TrackMatcher(clock)

    /** @throws InvalidTrackException when the file is not a readable track. */
    suspend operator fun invoke(file: NewAttachment): IgcImportDraft {
        val read = reader.read(file.bytes)
        val detected = detectFlight(read.track.fixes)
        val logbook = flights.findAll().items
        val duplicate = logbook.firstOrNull { it.igcFile?.checksum == read.checksum }
        val firstFix =
            read.track.fixes
                .firstOrNull()
                ?.time
        // The day the pilot lived, like the take-off time; the file header only knows the UTC date.
        val date = (detected?.takeOff ?: firstFix)?.let { clock.local(it).date } ?: read.track.date
        val match =
            firstFix?.let { matcher.match(TrackFacts(file.fileName, it, detected?.durationInMinutes), logbook) }
        return IgcImportDraft(
            fileName = file.fileName,
            checksum = read.checksum,
            date = date,
            takeOffTime = detected?.takeOff?.let { clock.local(it).time.inWholeMinutes() },
            landingTime = detected?.landing?.let { clock.local(it).time.inWholeMinutes() },
            duration = detected?.durationInMinutes,
            duplicateOf = duplicate,
            match = match,
            dayHasFlights = logbook.any { it.details.date == date },
        )
    }
}

private fun LocalTime.inWholeMinutes() = LocalTime(hour, minute)

/** Logs a flight with the original track attached. The same date rules apply as for a flight entered by hand. */
@OptIn(ExperimentalUuidApi::class)
class ImportIgcFlight(
    private val flights: FlightRepository,
    wings: WingRepository,
    private val attachments: AttachmentStore,
    private val clock: AppClock,
    private val transactions: LogbookTransactions,
    // Injectable so tests get deterministic ids.
    private val newId: () -> FlightId = { FlightId(Uuid.random().toString()) },
) {
    private val rules = FlightRules(wings, clock)

    suspend operator fun invoke(
        details: FlightDetails,
        file: NewAttachment,
        checksum: String,
    ): Flight {
        val id = newId()
        return transactions.run(LogbookChange.FlightImportedFromTrack(id)) {
            rules.ensureAllowed(details)
            withStoredFiles(attachments, listOf(file)) { stored ->
                Flight(id, details, clock.now(), IgcFile(stored.single(), checksum)).also { flights.save(it) }
            }
        }
    }
}

/** Adds a track to a flight already in the logbook, keeping its details as they are (ADR 0026). */
class AttachIgcTrack(
    private val flights: FlightRepository,
    private val attachments: AttachmentStore,
    private val transactions: LogbookTransactions,
) {
    /**
     * @throws FlightNotFoundException when the flight was deleted meanwhile.
     * @throws FlightHasTrackException when the flight got a track meanwhile; it is never replaced.
     */
    suspend operator fun invoke(
        flightId: FlightId,
        file: NewAttachment,
        checksum: String,
    ): Flight =
        transactions.run(LogbookChange.TrackAttached(flightId)) {
            val flight = flights.findById(flightId) ?: throw FlightNotFoundException(flightId)
            if (flight.igcFile != null) throw FlightHasTrackException(flightId)
            withStoredFiles(attachments, listOf(file)) { stored ->
                flight.attachTrack(IgcFile(stored.single(), checksum)).also { flights.save(it) }
            }
        }
}
