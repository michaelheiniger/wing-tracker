package ch.qscqlmpa.wingtracker.ui.gear.wing

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.CurrencyCode
import ch.qscqlmpa.wingtracker.gear.Price
import ch.qscqlmpa.wingtracker.gear.wing.CertificationClass
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// Own factory: UI tests do not share fixtures with the domain or persistence tests.
private fun aFormInput() =
    WingFormInput(
        name = "Sky Dancer",
        manufacturer = "Advance",
        model = "Iota 3",
        size = "M",
        serialNumber = "ADV-2023-04817",
        certificationClass = CertificationClass.B,
        condition = AcquisitionCondition.SecondHand,
        purchaseDate = LocalDate(2024, 3, 2),
        priceAmount = "2150.50",
        priceCurrency = "chf",
        inServiceDate = LocalDate(2024, 3, 9),
        hoursBeforeTracking = "42:30",
    )

private fun errorsOf(input: WingFormInput) = (input.validate() as WingFormResult.Invalid).errors

class WingFormTest {
    @Nested
    inner class Defaults {
        @Test
        fun `a new form starts with the default currency`() {
            // given / when
            val input = WingFormInput()

            // then
            assertThat(input.priceCurrency).isEqualTo("CHF")
        }
    }

    @Nested
    inner class Validate {
        @Test
        fun `builds the wing details from a fully populated form`() {
            // given
            val input = aFormInput()

            // when
            val result = input.validate()

            // then
            val expected =
                WingDetails(
                    name = "Sky Dancer",
                    manufacturer = "Advance",
                    model = "Iota 3",
                    size = "M",
                    serialNumber = "ADV-2023-04817",
                    certificationClass = CertificationClass.B,
                    acquisition =
                        Acquisition(
                            AcquisitionCondition.SecondHand,
                            LocalDate(2024, 3, 2),
                            Price(215_050, CurrencyCode("CHF")),
                        ),
                    inServiceDate = LocalDate(2024, 3, 9),
                    hoursBeforeTracking = 42.hours + 30.minutes,
                )
            assertThat(result).usingRecursiveComparison().isEqualTo(WingFormResult.Valid(expected))
        }

        @Test
        fun `turns blank optional text into absent values`() {
            // given
            val input =
                aFormInput().copy(size = " ", serialNumber = "", purchaseDate = null).copy(priceAmount = "")

            // when
            val details = (input.validate() as WingFormResult.Valid).details

            // then
            assertThat(details.size).isNull()
            assertThat(details.serialNumber).isNull()
            assertThat(details.acquisition.purchaseDate).isNull()
            assertThat(details.acquisition.price).isNull()
        }

        @Test
        fun `ignores the pre-filled currency when no amount is given`() {
            // given
            val input = aFormInput().copy(priceAmount = "", priceCurrency = "CHF")

            // when
            val result = input.validate()

            // then
            assertThat(result).isInstanceOf(WingFormResult.Valid::class.java)
        }

        @Test
        fun `treats empty hours before tracking as zero`() {
            // given
            val input = aFormInput().copy(hoursBeforeTracking = "")

            // when
            val details = (input.validate() as WingFormResult.Valid).details

            // then
            assertThat(details.hoursBeforeTracking).isEqualTo(Duration.ZERO)
        }

        @Test
        fun `accepts whole hours and a whole price`() {
            // given
            val input = aFormInput().copy(hoursBeforeTracking = "120", priceAmount = "3490")

            // when
            val details = (input.validate() as WingFormResult.Valid).details

            // then
            assertThat(details.hoursBeforeTracking).isEqualTo(120.hours)
            assertThat(details.acquisition.price?.minorUnits).isEqualTo(349_000)
        }

        @Test
        fun `reports every missing mandatory field at once`() {
            // given
            val input = WingFormInput()

            // when
            val errors = errorsOf(input)

            // then
            assertThat(errors).containsOnlyKeys(
                WingFormField.Name,
                WingFormField.Manufacturer,
                WingFormField.Model,
                WingFormField.InServiceDate,
            )
            assertThat(errors.values).containsOnly(WingFormError.Required)
        }

        @Test
        fun `rejects hours with invalid minutes`() {
            // given
            val input = aFormInput().copy(hoursBeforeTracking = "42:75")

            // when
            val errors = errorsOf(input)

            // then
            assertThat(errors).containsEntry(WingFormField.HoursBeforeTracking, WingFormError.InvalidHours)
        }

        @Test
        fun `rejects an invalid currency code when a price is given`() {
            // given
            val input = aFormInput().copy(priceCurrency = "FRANC")

            // when
            val errors = errorsOf(input)

            // then
            assertThat(errors).containsEntry(WingFormField.PriceCurrency, WingFormError.InvalidCurrency)
        }

        @Test
        fun `rejects a price with a negative sign`() {
            // given
            val input = aFormInput().copy(priceAmount = "-5")

            // when
            val errors = errorsOf(input)

            // then
            assertThat(errors).containsEntry(WingFormField.PriceAmount, WingFormError.InvalidAmount)
        }
    }

    @Nested
    inner class From {
        @Test
        fun `round-trips an existing wing through the form`() {
            // given
            val details = (aFormInput().validate() as WingFormResult.Valid).details
            val wing = Wing(WingId("3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47"), details)

            // when
            val result = WingFormInput.from(wing).validate()

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(WingFormResult.Valid(details))
        }

        @Test
        fun `keeps the default currency for a wing without a price`() {
            // given
            val details = (aFormInput().copy(priceAmount = "").validate() as WingFormResult.Valid).details
            val wing = Wing(WingId("3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47"), details)

            // when
            val input = WingFormInput.from(wing)

            // then
            assertThat(input.priceCurrency).isEqualTo("CHF")
        }
    }
}
