package ch.qscqlmpa.wingtracker.ui.flights

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
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
