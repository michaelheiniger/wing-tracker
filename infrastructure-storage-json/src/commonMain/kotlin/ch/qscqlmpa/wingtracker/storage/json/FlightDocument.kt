package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
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
        )
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

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
            )
    }
}
