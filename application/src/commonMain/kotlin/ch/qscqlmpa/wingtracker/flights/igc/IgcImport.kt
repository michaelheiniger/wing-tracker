package ch.qscqlmpa.wingtracker.flights.igc

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.FlightRules
import ch.qscqlmpa.wingtracker.flights.IgcFile
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.gear.maintenance.withStoredFiles
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
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
)

class PrepareIgcImport(
    private val reader: TrackReader,
    private val flights: FlightRepository,
    private val clock: AppClock,
) {
    /** @throws InvalidTrackException when the file is not a readable track. */
    suspend operator fun invoke(file: NewAttachment): IgcImportDraft {
        val read = reader.read(file.bytes)
        val detected = detectFlight(read.track.fixes)
        val duplicate = flights.findAll().items.firstOrNull { it.igcFile?.checksum == read.checksum }
        return IgcImportDraft(
            fileName = file.fileName,
            checksum = read.checksum,
            date = read.track.date,
            takeOffTime = detected?.takeOff?.let { clock.local(it).time.inWholeMinutes() },
            landingTime = detected?.landing?.let { clock.local(it).time.inWholeMinutes() },
            duration = detected?.durationInMinutes,
            duplicateOf = duplicate,
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
    // Injectable so tests get deterministic ids.
    private val newId: () -> FlightId = { FlightId(Uuid.random().toString()) },
) {
    private val rules = FlightRules(wings, clock)

    suspend operator fun invoke(
        details: FlightDetails,
        file: NewAttachment,
        checksum: String,
    ): Flight {
        rules.ensureAllowed(details)
        return withStoredFiles(attachments, listOf(file)) { stored ->
            Flight(newId(), details, clock.now(), IgcFile(stored.single(), checksum)).also { flights.save(it) }
        }
    }
}
