package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.runtime.Composable
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.SiteNames
import ch.qscqlmpa.wingtracker.flights.SiteSource
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.ui.formatHoursAndMinutes
import ch.qscqlmpa.wingtracker.ui.parseDate
import ch.qscqlmpa.wingtracker.ui.parseHoursAndMinutes
import ch.qscqlmpa.wingtracker.ui.parseTimeOfDay
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.error_date
import ch.qscqlmpa.wingtracker.ui.resources.error_date_future
import ch.qscqlmpa.wingtracker.ui.resources.error_duration
import ch.qscqlmpa.wingtracker.ui.resources.error_number
import ch.qscqlmpa.wingtracker.ui.resources.error_required
import ch.qscqlmpa.wingtracker.ui.resources.error_time
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.stringResource

/**
 * What the pilot typed or picked; parsing lives in [validate] so it is testable without any UI. The site fields hold
 * the pilot's own names; [takeOffSiteNames] and [landingSiteNames] keep what the other sources gave (ADR 0030).
 */
data class FlightFormInput(
    val date: String = "",
    val takeOffTime: String = "",
    val wingId: WingId? = null,
    val duration: String = "",
    val takeOffSite: String = "",
    val landingSite: String = "",
    val notes: String = "",
    val distanceKm: String = "",
    val topAltitudeMeters: String = "",
    val instrument: String = "",
    val takeOffSiteNames: SiteNames = SiteNames.NONE,
    val landingSiteNames: SiteNames = SiteNames.NONE,
) {
    companion object {
        fun from(flight: Flight): FlightFormInput =
            FlightFormInput(
                date = flight.details.date.toDisplay(),
                takeOffTime =
                    flight.details.takeOffTime
                        ?.toDisplay()
                        .orEmpty(),
                wingId = flight.details.wingId,
                duration = formatHoursAndMinutes(flight.details.duration.inWholeMinutes),
                takeOffSite = flight.details.takeOffSite[SiteSource.Manual].orEmpty(),
                landingSite = flight.details.landingSite[SiteSource.Manual].orEmpty(),
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
                takeOffSiteNames = flight.details.takeOffSite,
                landingSiteNames = flight.details.landingSite,
            )
    }
}

enum class FlightFormField { Date, TakeOffTime, Wing, Duration, Distance, TopAltitude }

/** Error kinds rather than text, so the screen can show them in the user's language (ADR 0014). */
enum class FlightFormError {
    Required,
    InvalidDate,
    InvalidTime,
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
    val flightDate = parseDate(date)
    if (flightDate == null) {
        errors[FlightFormField.Date] = if (date.isBlank()) FlightFormError.Required else FlightFormError.InvalidDate
    }
    val time = parseTimeOfDay(takeOffTime)
    if (takeOffTime.isNotBlank() && time == null) errors[FlightFormField.TakeOffTime] = FlightFormError.InvalidTime
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
            date = checkNotNull(flightDate),
            duration = checkNotNull(flightDuration),
            wingId = checkNotNull(wingId),
            takeOffSite = takeOffSiteNames.with(SiteSource.Manual, takeOffSite),
            landingSite = landingSiteNames.with(SiteSource.Manual, landingSite),
            notes = notes.trim().ifEmpty { null },
            takeOffTime = time,
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
        FlightFormError.InvalidDate -> stringResource(Res.string.error_date)
        FlightFormError.InvalidTime -> stringResource(Res.string.error_time)
        FlightFormError.InvalidDuration -> stringResource(Res.string.error_duration)
        FlightFormError.InvalidNumber -> stringResource(Res.string.error_number)
        FlightFormError.DateInFuture -> stringResource(Res.string.error_date_future)
    }
