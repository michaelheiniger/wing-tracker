package ch.qscqlmpa.wingtracker.ui.gear.simple

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.CurrencyCode
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.Price
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// Own factory: UI tests do not share fixtures with the other modules' tests.
private fun aFormInput() =
    SimpleGearFormInput(
        name = "Rescue",
        manufacturer = "Gin",
        model = "Yeti Cross",
        size = "XL",
        serialNumber = "GIN-R-7731",
        manufactureDate = LocalDate(2023, 11, 5),
        condition = AcquisitionCondition.SecondHand,
        purchaseDate = LocalDate(2024, 2, 20),
        priceAmount = "480.50",
        priceCurrency = "chf",
        inServiceDate = LocalDate(2024, 3, 1),
        intervalMonths = "12",
    )

private fun errorsOf(input: SimpleGearFormInput) = (input.validate() as SimpleGearFormResult.Invalid).errors

class SimpleGearFormTest {
    @Nested
    inner class Validate {
        @Test
        fun `builds the details from a fully populated form`() {
            // given
            val input = aFormInput()

            // when
            val result = input.validate()

            // then
            val expected =
                SimpleGearDetails(
                    name = "Rescue",
                    manufacturer = "Gin",
                    model = "Yeti Cross",
                    size = "XL",
                    serialNumber = "GIN-R-7731",
                    manufactureDate = LocalDate(2023, 11, 5),
                    acquisition =
                        Acquisition(
                            AcquisitionCondition.SecondHand,
                            LocalDate(2024, 2, 20),
                            Price(48_050, CurrencyCode("CHF")),
                        ),
                    inServiceDate = LocalDate(2024, 3, 1),
                    intervalMonths = 12,
                )
            assertThat(result).usingRecursiveComparison().isEqualTo(SimpleGearFormResult.Valid(expected))
        }

        @Test
        fun `has no interval when the months are empty`() {
            // given
            val input = aFormInput().copy(intervalMonths = "")

            // when
            val details = (input.validate() as SimpleGearFormResult.Valid).details

            // then
            assertThat(details.intervalMonths).isNull()
        }

        @Test
        fun `ignores the pre-filled currency when no amount is given`() {
            // given
            val input = aFormInput().copy(priceAmount = "")

            // when
            val details = (input.validate() as SimpleGearFormResult.Valid).details

            // then
            assertThat(details.acquisition.price).isNull()
        }

        @Test
        fun `reports every missing mandatory field at once`() {
            // given
            val input = SimpleGearFormInput()

            // when
            val errors = errorsOf(input)

            // then
            assertThat(errors).containsOnlyKeys(
                SimpleGearField.Name,
                SimpleGearField.Manufacturer,
                SimpleGearField.Model,
            )
            assertThat(errors.values).containsOnly(FormError.Required)
        }

        @Test
        fun `accepts gear without an in-service date`() {
            // given
            val input = aFormInput().copy(inServiceDate = null)

            // when
            val result = input.validate()

            // then
            assertThat((result as SimpleGearFormResult.Valid).details.inServiceDate).isNull()
        }

        @Test
        fun `rejects months that are not a positive whole number`() {
            // given
            val input = aFormInput().copy(intervalMonths = "0")

            // when
            val errors = errorsOf(input)

            // then
            assertThat(errors).containsEntry(SimpleGearField.IntervalMonths, FormError.InvalidMonths)
        }

        @Test
        fun `rejects a price that is not a number`() {
            // given
            val input = aFormInput().copy(priceAmount = "a lot")

            // when
            val errors = errorsOf(input)

            // then
            assertThat(errors).containsEntry(SimpleGearField.PriceAmount, FormError.InvalidAmount)
        }

        @Test
        fun `rejects an invalid currency code when a price is given`() {
            // given
            val input = aFormInput().copy(priceCurrency = "FRANC")

            // when
            val errors = errorsOf(input)

            // then
            assertThat(errors).containsEntry(SimpleGearField.PriceCurrency, FormError.InvalidCurrency)
        }
    }

    @Nested
    inner class From {
        @Test
        fun `round-trips an existing item through the form`() {
            // given
            val details = (aFormInput().validate() as SimpleGearFormResult.Valid).details
            val item = SimpleGearItem(GearRef(GearKind.Reserve, "reserve-1"), details, GearStatus.Active, false, null)

            // when
            val result = SimpleGearFormInput.from(item).validate()

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(SimpleGearFormResult.Valid(details))
        }

        @Test
        fun `keeps the default currency for an item without a price`() {
            // given
            val details = (aFormInput().copy(priceAmount = "").validate() as SimpleGearFormResult.Valid).details
            val item = SimpleGearItem(GearRef(GearKind.Harness, "harness-1"), details, GearStatus.Active, false, null)

            // when
            val input = SimpleGearFormInput.from(item)

            // then
            assertThat(input.priceCurrency).isEqualTo("CHF")
        }
    }
}
