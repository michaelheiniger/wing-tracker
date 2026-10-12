package ch.qscqlmpa.wingtracker.ui.flights

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Own factory: UI tests do not share fixtures with the domain or persistence tests.
private fun aFlightFormInput() =
    FlightFormInput(
        date = LocalDate(2026, 9, 20),
        wingId = WingId("3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47"),
        duration = "1:35",
        takeOffSite = "Niederhorn",
        landingSite = "Beatenbucht",
        notes = "Thermic, smooth air",
    )

private fun errorsOf(input: FlightFormInput) = (input.validate() as FlightFormResult.Invalid).errors

class FlightFormTest {
    @Nested
    inner class Validate {
        @Test
        fun `builds the flight details from a fully populated form`() {
            // given
            val input = aFlightFormInput()

            // when
            val result = input.validate()

            // then
            val expected =
                FlightDetails(
                    date = LocalDate(2026, 9, 20),
                    duration = 95.minutes,
                    wingId = WingId("3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47"),
                    takeOffSite = "Niederhorn",
                    landingSite = "Beatenbucht",
                    notes = "Thermic, smooth air",
                )
            assertThat(result).usingRecursiveComparison().isEqualTo(FlightFormResult.Valid(expected))
        }

        @Test
        fun `turns blank sites and notes into absent values`() {
            // given
            val input = aFlightFormInput().copy(takeOffSite = " ", landingSite = "", notes = "  ")

            // when
            val details = (input.validate() as FlightFormResult.Valid).details

            // then
            assertThat(details.takeOffSite).isNull()
            assertThat(details.landingSite).isNull()
            assertThat(details.notes).isNull()
        }

        @Test
        fun `reports every missing mandatory field at once`() {
            // given
            val input = FlightFormInput()

            // when
            val errors = errorsOf(input)

            // then
            assertThat(errors).containsOnlyKeys(FlightFormField.Date, FlightFormField.Wing, FlightFormField.Duration)
            assertThat(errors.values).containsOnly(FlightFormError.Required)
        }

        @Test
        fun `rejects a duration that is not hours and minutes`() {
            // given
            val input = aFlightFormInput().copy(duration = "ninety")

            // when
            val errors = errorsOf(input)

            // then
            assertThat(errors).containsEntry(FlightFormField.Duration, FlightFormError.InvalidDuration)
        }

        @Test
        fun `rejects a duration of zero`() {
            // given
            val input = aFlightFormInput().copy(duration = "0:00")

            // when
            val errors = errorsOf(input)

            // then
            assertThat(errors).containsEntry(FlightFormField.Duration, FlightFormError.InvalidDuration)
        }
    }

    @Nested
    inner class FlightExtras {
        @Test
        fun `reads distance, altitude and instrument, accepting a decimal comma`() {
            // given
            val input =
                aFlightFormInput().copy(
                    distanceKm = "15,5",
                    topAltitudeMeters = "2464",
                    instrument = " Nav XL ",
                )

            // when
            val details = (input.validate() as FlightFormResult.Valid).details

            // then
            assertThat(details.distanceKm).isEqualTo(15.5)
            assertThat(details.topAltitudeMeters).isEqualTo(2464)
            assertThat(details.instrument).isEqualTo("Nav XL")
        }

        @Test
        fun `treats blank extras as absent`() {
            // given
            val input = aFlightFormInput().copy(distanceKm = " ", topAltitudeMeters = "", instrument = " ")

            // when
            val details = (input.validate() as FlightFormResult.Valid).details

            // then
            assertThat(details.distanceKm).isNull()
            assertThat(details.topAltitudeMeters).isNull()
            assertThat(details.instrument).isNull()
        }

        @Test
        fun `rejects a distance or altitude that is not a non-negative number`() {
            // given
            val input = aFlightFormInput().copy(distanceKm = "far", topAltitudeMeters = "-3")

            // when
            val errors = errorsOf(input)

            // then
            assertThat(errors).containsOnly(
                entry(FlightFormField.Distance, FlightFormError.InvalidNumber),
                entry(FlightFormField.TopAltitude, FlightFormError.InvalidNumber),
            )
        }

        @Test
        fun `keeps the extras of an imported flight when it is edited`() {
            // given
            val base = (aFlightFormInput().validate() as FlightFormResult.Valid).details
            val details = base.copy(distanceKm = 15.0, topAltitudeMeters = 2464, instrument = "Nav XL")
            val flight = Flight(FlightId("flight-1"), details, Instant.parse("2026-09-20T17:00:00Z"))

            // when
            val input = FlightFormInput.from(flight)

            // then
            assertThat(input.distanceKm).isEqualTo("15")
            assertThat(input.validate()).usingRecursiveComparison().isEqualTo(FlightFormResult.Valid(details))
        }
    }

    @Nested
    inner class From {
        @Test
        fun `round-trips an existing flight through the form`() {
            // given
            val details = (aFlightFormInput().validate() as FlightFormResult.Valid).details
            val flight = Flight(FlightId("flight-1"), details, Instant.parse("2026-09-20T17:00:00Z"))

            // when
            val result = FlightFormInput.from(flight).validate()

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(FlightFormResult.Valid(details))
        }
    }
}
