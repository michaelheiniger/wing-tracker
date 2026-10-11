package ch.qscqlmpa.wingtracker.ui.gear.simple

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.ui.gear.DEFAULT_CURRENCY
import ch.qscqlmpa.wingtracker.ui.gear.PriceParse
import ch.qscqlmpa.wingtracker.ui.gear.formatAmount
import ch.qscqlmpa.wingtracker.ui.gear.parsePrice
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError
import kotlinx.datetime.LocalDate

/** What the pilot typed or picked for a harness or reserve; parsing lives in [validate] (testable without UI). */
data class SimpleGearFormInput(
    val name: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val size: String = "",
    val serialNumber: String = "",
    val manufactureDate: LocalDate? = null,
    val condition: AcquisitionCondition = AcquisitionCondition.New,
    val purchaseDate: LocalDate? = null,
    val priceAmount: String = "",
    val priceCurrency: String = DEFAULT_CURRENCY,
    val inServiceDate: LocalDate? = null,
    val intervalMonths: String = "",
) {
    companion object {
        fun from(item: SimpleGearItem): SimpleGearFormInput {
            val details = item.details
            val price = details.acquisition.price
            return SimpleGearFormInput(
                name = details.name,
                manufacturer = details.manufacturer,
                model = details.model,
                size = details.size.orEmpty(),
                serialNumber = details.serialNumber.orEmpty(),
                manufactureDate = details.manufactureDate,
                condition = details.acquisition.condition,
                purchaseDate = details.acquisition.purchaseDate,
                priceAmount = price?.let { formatAmount(it.minorUnits) }.orEmpty(),
                priceCurrency = price?.currency?.value ?: DEFAULT_CURRENCY,
                inServiceDate = details.inServiceDate,
                intervalMonths = details.intervalMonths?.toString().orEmpty(),
            )
        }
    }
}

enum class SimpleGearField { Name, Manufacturer, Model, InServiceDate, PriceAmount, PriceCurrency, IntervalMonths }

sealed interface SimpleGearFormResult {
    data class Valid(
        val details: SimpleGearDetails,
    ) : SimpleGearFormResult

    data class Invalid(
        val errors: Map<SimpleGearField, FormError>,
    ) : SimpleGearFormResult
}

private val MONTHS = Regex("""\d{1,3}""")

fun SimpleGearFormInput.validate(): SimpleGearFormResult {
    val errors = linkedMapOf<SimpleGearField, FormError>()
    if (name.isBlank()) errors[SimpleGearField.Name] = FormError.Required
    if (manufacturer.isBlank()) errors[SimpleGearField.Manufacturer] = FormError.Required
    if (model.isBlank()) errors[SimpleGearField.Model] = FormError.Required
    if (inServiceDate == null) errors[SimpleGearField.InServiceDate] = FormError.Required

    val price =
        when (val parsed = parsePrice(priceAmount, priceCurrency)) {
            is PriceParse.Parsed -> {
                parsed.price
            }

            is PriceParse.Invalid -> {
                if (parsed.amountInvalid) errors[SimpleGearField.PriceAmount] = FormError.InvalidAmount
                if (parsed.currencyInvalid) errors[SimpleGearField.PriceCurrency] = FormError.InvalidCurrency
                null
            }
        }
    val months = intervalMonths.trim()
    val intervalValue =
        months
            .takeIf { it.isNotEmpty() }
            ?.takeIf { MONTHS.matches(it) }
            ?.toInt()
            ?.takeIf { it > 0 }
    if (months.isNotEmpty() && intervalValue == null) errors[SimpleGearField.IntervalMonths] = FormError.InvalidMonths

    if (inServiceDate == null || errors.isNotEmpty()) return SimpleGearFormResult.Invalid(errors)
    return SimpleGearFormResult.Valid(
        SimpleGearDetails(
            name = name.trim(),
            manufacturer = manufacturer.trim(),
            model = model.trim(),
            size = size.trim().ifEmpty { null },
            serialNumber = serialNumber.trim().ifEmpty { null },
            manufactureDate = manufactureDate,
            acquisition = Acquisition(condition, purchaseDate, price),
            inServiceDate = inServiceDate,
            intervalMonths = intervalValue,
        ),
    )
}
