package ch.qscqlmpa.wingtracker.ui.gear.simple

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.ui.gear.DEFAULT_CURRENCY
import ch.qscqlmpa.wingtracker.ui.gear.PriceParse
import ch.qscqlmpa.wingtracker.ui.gear.formatAmount
import ch.qscqlmpa.wingtracker.ui.gear.parseOptionalDate
import ch.qscqlmpa.wingtracker.ui.gear.parsePrice
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError
import ch.qscqlmpa.wingtracker.ui.toDisplay

/** What the pilot typed or picked for a harness or reserve; parsing lives in [validate] (testable without UI). */
data class SimpleGearFormInput(
    val name: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val size: String = "",
    val serialNumber: String = "",
    val manufactureDate: String = "",
    val condition: AcquisitionCondition = AcquisitionCondition.New,
    val purchaseDate: String = "",
    val priceAmount: String = "",
    val priceCurrency: String = DEFAULT_CURRENCY,
    val inServiceDate: String = "",
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
                manufactureDate = details.manufactureDate?.toDisplay().orEmpty(),
                condition = details.acquisition.condition,
                purchaseDate =
                    details.acquisition.purchaseDate
                        ?.toDisplay()
                        .orEmpty(),
                priceAmount = price?.let { formatAmount(it.minorUnits) }.orEmpty(),
                priceCurrency = price?.currency?.value ?: DEFAULT_CURRENCY,
                inServiceDate = details.inServiceDate?.toDisplay().orEmpty(),
                intervalMonths = details.intervalMonths?.toString().orEmpty(),
            )
        }
    }
}

enum class SimpleGearField {
    Name,
    Manufacturer,
    Model,
    ManufactureDate,
    PurchaseDate,
    PriceAmount,
    PriceCurrency,
    InServiceDate,
    IntervalMonths,
}

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

    val manufactured =
        parseOptionalDate(manufactureDate) { errors[SimpleGearField.ManufactureDate] = FormError.InvalidDate }
    val purchased = parseOptionalDate(purchaseDate) { errors[SimpleGearField.PurchaseDate] = FormError.InvalidDate }
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
    val inService = parseOptionalDate(inServiceDate) { errors[SimpleGearField.InServiceDate] = FormError.InvalidDate }
    val months = intervalMonths.trim()
    val intervalValue =
        months
            .takeIf { it.isNotEmpty() }
            ?.takeIf { MONTHS.matches(it) }
            ?.toInt()
            ?.takeIf { it > 0 }
    if (months.isNotEmpty() && intervalValue == null) errors[SimpleGearField.IntervalMonths] = FormError.InvalidMonths

    if (errors.isNotEmpty()) return SimpleGearFormResult.Invalid(errors)
    return SimpleGearFormResult.Valid(
        SimpleGearDetails(
            name = name.trim(),
            manufacturer = manufacturer.trim(),
            model = model.trim(),
            size = size.trim().ifEmpty { null },
            serialNumber = serialNumber.trim().ifEmpty { null },
            manufactureDate = manufactured,
            acquisition = Acquisition(condition, purchased, price),
            inServiceDate = inService,
            intervalMonths = intervalValue,
        ),
    )
}
