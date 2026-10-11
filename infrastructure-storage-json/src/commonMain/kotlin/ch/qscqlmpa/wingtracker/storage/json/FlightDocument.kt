package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
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
) {
    fun toDomain(): Flight {
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw UnsupportedSchemaException("Flight $id was written by a newer version of the application")
        }
        return Flight(
            id = FlightId(id),
            details =
                FlightDetails(
                    date = date,
                    duration = durationMinutes.minutes,
                    wingId = WingId(wingId),
                    takeOffSite = takeOffSite,
                    landingSite = landingSite,
                    notes = notes,
                ),
            loggedAt = Instant.parse(loggedAt),
            igcFile = igcFile(),
        )
    }

    private fun igcFile(): IgcFile? {
        if (igcAttachmentId == null || igcFileName == null || igcChecksum == null) return null
        return IgcFile(Attachment(AttachmentId(igcAttachmentId), igcFileName), igcChecksum)
    }

    companion object {
        // Version 2 added the imported track. Older application versions treat it as newer data and leave it untouched.
        const val CURRENT_SCHEMA_VERSION = 2

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
            )
    }
}
