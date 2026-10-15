package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.flights.ExternalReference
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.IgcFile
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** On-disk shape of a flight, kept apart from the domain model (see [WingDocument]). */
@Serializable
internal data class FlightDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val date: LocalDate,
    val durationMinutes: Long,
    val wingId: String,
    val takeOffSite: String? = null,
    val landingSite: String? = null,
    val notes: String? = null,
    // ISO-8601 text keeps the file readable and independent of the serialization library's Instant support.
    val loggedAt: String,
    // Added in schema 2: the original track a flight was imported from.
    val igcAttachmentId: String? = null,
    val igcFileName: String? = null,
    val igcChecksum: String? = null,
    // Added in schema 3: details and origin of a flight imported from another service.
    val distanceKm: Double? = null,
    val topAltitudeMeters: Int? = null,
    val instrument: String? = null,
    val importedFromSystem: String? = null,
    val importedFromId: String? = null,
) {
    fun toDomain(): Flight =
        Flight(
            id = FlightId(id),
            details =
                FlightDetails(
                    date = date,
                    duration = durationMinutes.minutes,
                    wingId = WingId(wingId),
                    takeOffSite = takeOffSite,
                    landingSite = landingSite,
                    notes = notes,
                    distanceKm = distanceKm,
                    topAltitudeMeters = topAltitudeMeters,
                    instrument = instrument,
                ),
            loggedAt = Instant.parse(loggedAt),
            igcFile = igcFile(),
            importedFrom = importedFrom(),
        )

    private fun importedFrom(): ExternalReference? {
        if (importedFromSystem == null || importedFromId == null) return null
        return ExternalReference(importedFromSystem, importedFromId)
    }

    private fun igcFile(): IgcFile? {
        if (igcAttachmentId == null || igcFileName == null || igcChecksum == null) return null
        return IgcFile(Attachment(AttachmentId(igcAttachmentId), igcFileName), igcChecksum)
    }

    companion object {
        // Version 2 added the imported track, version 3 the details imported from other services. Older application
        // versions treat newer data as unsupported and leave it untouched.
        const val CURRENT_SCHEMA_VERSION = 3

        fun from(flight: Flight) =
            FlightDocument(
                id = flight.id.value,
                date = flight.details.date,
                durationMinutes = flight.details.duration.inWholeMinutes,
                wingId = flight.details.wingId.value,
                takeOffSite = flight.details.takeOffSite,
                landingSite = flight.details.landingSite,
                notes = flight.details.notes,
                loggedAt = flight.loggedAt.toString(),
                igcAttachmentId =
                    flight.igcFile
                        ?.attachment
                        ?.id
                        ?.value,
                igcFileName = flight.igcFile?.attachment?.fileName,
                igcChecksum = flight.igcFile?.checksum,
                distanceKm = flight.details.distanceKm,
                topAltitudeMeters = flight.details.topAltitudeMeters,
                instrument = flight.details.instrument,
                importedFromSystem = flight.importedFrom?.system,
                importedFromId = flight.importedFrom?.id,
            )
    }
}
