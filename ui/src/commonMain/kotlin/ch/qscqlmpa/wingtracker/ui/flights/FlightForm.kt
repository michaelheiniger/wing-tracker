package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.runtime.Composable
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.ui.formatHoursAndMinutes
import ch.qscqlmpa.wingtracker.ui.parseHoursAndMinutes
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.error_date_future
import ch.qscqlmpa.wingtracker.ui.resources.error_duration
import ch.qscqlmpa.wingtracker.ui.resources.error_number
import ch.qscqlmpa.wingtracker.ui.resources.error_required
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** What the pilot typed or picked; parsing lives in [validate] so it is testable without any UI. */
data class FlightFormInput(
    val date: LocalDate? = null,
    val wingId: WingId? = null,
    val duration: String = "",
    val takeOffSite: String = "",
    val landingSite: String = "",
    val notes: String = "",
    val distanceKm: String = "",
    val topAltitudeMeters: String = "",
    val instrument: String = "",
) {
    companion object {
        fun from(flight: Flight): FlightFormInput =
            FlightFormInput(
                date = flight.details.date,
                wingId = flight.details.wingId,
                duration = formatHoursAndMinutes(flight.details.duration.inWholeMinutes),
                takeOffSite = flight.details.takeOffSite.orEmpty(),
                landingSite = flight.details.landingSite.orEmpty(),
                notes = flight.details.notes.orEmpty(),
                distanceKm =
                    flight.details.distanceKm
                        ?.toDisplayNumber()
                        .orEmpty(),
                topAltitudeMeters =
                    flight.details.topAltitudeMeters
                        ?.toString()
                        .orEmpty(),
                instrument = flight.details.instrument.orEmpty(),
            )
    }
}

enum class FlightFormField { Date, Wing, Duration, Distance, TopAltitude }

/** Error kinds rather than text, so the screen can show them in the user's language (ADR 0014). */
enum class FlightFormError {
    Required,
    InvalidDuration,
    InvalidNumber,
    DateInFuture,
}

sealed interface FlightFormResult {
    data class Valid(
        val details: FlightDetails,
    ) : FlightFormResult

    data class Invalid(
        val errors: Map<FlightFormField, FlightFormError>,
    ) : FlightFormResult
}

fun FlightFormInput.validate(): FlightFormResult {
    val errors = linkedMapOf<FlightFormField, FlightFormError>()
    if (date == null) errors[FlightFormField.Date] = FlightFormError.Required
    if (wingId == null) errors[FlightFormField.Wing] = FlightFormError.Required

    val parsed = parseHoursAndMinutes(duration)
    val flightDuration = parsed?.takeIf { it.isPositive() }
    if (flightDuration == null) {
        errors[FlightFormField.Duration] =
            if (duration.isBlank()) FlightFormError.Required else FlightFormError.InvalidDuration
    }

    val distance =
        distanceKm
            .trim()
            .replace(',', '.')
            .toDoubleOrNull()
            ?.takeIf { it >= 0 && it.isFinite() }
    if (distanceKm.isNotBlank() && distance == null) errors[FlightFormField.Distance] = FlightFormError.InvalidNumber
    val altitude = topAltitudeMeters.trim().toIntOrNull()?.takeIf { it >= 0 }
    if (topAltitudeMeters.isNotBlank() && altitude == null) {
        errors[FlightFormField.TopAltitude] = FlightFormError.InvalidNumber
    }

    if (errors.isNotEmpty()) return FlightFormResult.Invalid(errors)

    // Every missing required value has an error above, so none is null here.
    return FlightFormResult.Valid(
        FlightDetails(
            date = checkNotNull(date),
            duration = checkNotNull(flightDuration),
            wingId = checkNotNull(wingId),
            takeOffSite = takeOffSite.trim().ifEmpty { null },
            landingSite = landingSite.trim().ifEmpty { null },
            notes = notes.trim().ifEmpty { null },
            distanceKm = distance,
            topAltitudeMeters = altitude,
            instrument = instrument.trim().ifEmpty { null },
        ),
    )
}

@Composable
fun FlightFormError.message(): String =
    when (this) {
        FlightFormError.Required -> stringResource(Res.string.error_required)
        FlightFormError.InvalidDuration -> stringResource(Res.string.error_duration)
        FlightFormError.InvalidNumber -> stringResource(Res.string.error_number)
        FlightFormError.DateInFuture -> stringResource(Res.string.error_date_future)
    }
