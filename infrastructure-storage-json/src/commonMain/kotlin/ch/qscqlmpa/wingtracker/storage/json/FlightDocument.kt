package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.flights.ExternalReference
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightExport
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.IgcFile
import ch.qscqlmpa.wingtracker.flights.SiteNames
import ch.qscqlmpa.wingtracker.flights.SiteSource
import ch.qscqlmpa.wingtracker.flights.syride.SYRIDE_SYSTEM
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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
    // Up to schema 5, a site had one name with no source; schema 6 replaced these with the names per source.
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
    // Added in schema 4: when the flight took off.
    val takeOffTime: LocalTime? = null,
    // Added in schema 5: the services the flight was sent to.
    val exports: List<FlightExportDocument> = emptyList(),
    // Added in schema 6: each site's name per source (ADR 0030).
    val takeOffSiteNames: List<SiteNameDocument> = emptyList(),
    val landingSiteNames: List<SiteNameDocument> = emptyList(),
) {
    fun toDomain(): Flight =
        Flight(
            id = FlightId(id),
            details =
                FlightDetails(
                    date = date,
                    duration = durationMinutes.minutes,
                    wingId = WingId(wingId),
                    takeOffSite = siteNames(takeOffSiteNames, takeOffSite, legacyTakeOffSource()),
                    landingSite = siteNames(landingSiteNames, landingSite, SiteSource.Manual),
                    notes = notes,
                    takeOffTime = takeOffTime,
                    distanceKm = distanceKm,
                    topAltitudeMeters = topAltitudeMeters,
                    instrument = instrument,
                ),
            loggedAt = Instant.parse(loggedAt),
            igcFile = igcFile(),
            importedFrom = importedFrom(),
            exports = exports.map { it.toDomain() },
        )

    private fun siteNames(
        names: List<SiteNameDocument>,
        legacyName: String?,
        legacySource: SiteSource,
    ): SiteNames =
        if (schemaVersion < SITE_NAMES_SCHEMA_VERSION) {
            SiteNames.of(legacySource, legacyName)
        } else {
            SiteNames(names.associate { it.source.toSource() to it.name })
        }

    // The source of a name stored without one is a guess: Syride only ever gave the take-off site, and a pilot may
    // have edited it since (ADR 0030).
    private fun legacyTakeOffSource() =
        if (importedFromSystem == SYRIDE_SYSTEM) SiteSource.Syride else SiteSource.Manual

    private fun importedFrom(): ExternalReference? {
        if (importedFromSystem == null || importedFromId == null) return null
        return ExternalReference(importedFromSystem, importedFromId)
    }

    private fun igcFile(): IgcFile? {
        if (igcAttachmentId == null || igcFileName == null || igcChecksum == null) return null
        return IgcFile(Attachment(AttachmentId(igcAttachmentId), igcFileName), igcChecksum)
    }

    companion object {
        // Version 2 added the imported track, version 3 the details imported from other services, version 4 the
        // take-off time, version 5 the exports, version 6 the site names per source. Older application versions treat
        // newer data as unsupported and leave it untouched.
        const val CURRENT_SCHEMA_VERSION = 6
        private const val SITE_NAMES_SCHEMA_VERSION = 6

        fun from(flight: Flight) =
            FlightDocument(
                id = flight.id.value,
                date = flight.details.date,
                durationMinutes = flight.details.duration.inWholeMinutes,
                wingId = flight.details.wingId.value,
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
                takeOffTime = flight.details.takeOffTime,
                exports = flight.exports.map { FlightExportDocument(it.system, it.at.toString(), it.externalId) },
                takeOffSiteNames = SiteNameDocument.from(flight.details.takeOffSite),
                landingSiteNames = SiteNameDocument.from(flight.details.landingSite),
            )
    }
}

@Serializable
internal data class FlightExportDocument(
    val system: String,
    // ISO-8601 text, like [FlightDocument.loggedAt].
    val at: String,
    val externalId: String? = null,
) {
    fun toDomain() = FlightExport(system, Instant.parse(at), externalId)
}

@Serializable
internal data class SiteNameDocument(
    val source: String,
    val name: String,
) {
    companion object {
        // Ordered by precedence, so the file reads like the flight page.
        fun from(names: SiteNames) =
            SiteSource.entries.mapNotNull { source ->
                names[source]?.let { SiteNameDocument(source.stored(), it) }
            }
    }
}

// The app's own words, so renaming an enum entry never changes stored data.
private fun SiteSource.stored(): String =
    when (this) {
        SiteSource.Manual -> "manual"
        SiteSource.Syride -> "syride"
        SiteSource.Spreadsheet -> "spreadsheet"
    }

private fun String.toSource(): SiteSource =
    SiteSource.entries.firstOrNull { it.stored() == this } ?: error("Unknown site name source: $this")
