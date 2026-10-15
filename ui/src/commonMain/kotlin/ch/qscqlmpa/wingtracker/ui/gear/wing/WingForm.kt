package ch.qscqlmpa.wingtracker.ui.gear.wing

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.Price
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.wing.CertificationClass
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.ui.formatHoursAndMinutes
import ch.qscqlmpa.wingtracker.ui.gear.DEFAULT_CURRENCY
import ch.qscqlmpa.wingtracker.ui.gear.PriceParse
import ch.qscqlmpa.wingtracker.ui.gear.formatAmount
import ch.qscqlmpa.wingtracker.ui.gear.parsePrice
import ch.qscqlmpa.wingtracker.ui.parseHoursAndMinutes
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/** What the pilot typed or picked; parsing lives in [validate] so it is testable without any UI. */
data class WingFormInput(
    val name: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val size: String = "",
    val serialNumber: String = "",
    val certificationClass: CertificationClass? = null,
    val condition: AcquisitionCondition = AcquisitionCondition.New,
    val purchaseDate: LocalDate? = null,
    val priceAmount: String = "",
    val priceCurrency: String = DEFAULT_CURRENCY,
    val inServiceDate: LocalDate? = null,
    val hoursBeforeTracking: String = "",
    val flightsBeforeTracking: String = "",
    val intervalMonths: String = "",
    val intervalHours: String = "",
    // Until the pilot types a name, it follows manufacturer and model; afterwards it is theirs to keep.
    val nameEdited: Boolean = false,
) {
    fun withName(value: String) = copy(name = value, nameEdited = true)

    fun withManufacturer(value: String) = copy(manufacturer = value).withSuggestedName()

    fun withModel(value: String) = copy(model = value).withSuggestedName()

    private fun withSuggestedName() = if (nameEdited) this else copy(name = "$manufacturer $model".trim())

    companion object {
        fun from(wing: Wing): WingFormInput {
            val details = wing.details
            val price = details.acquisition.price
            return WingFormInput(
                // An existing name must never be overwritten by a later change of manufacturer or model.
                nameEdited = true,
                name = details.name,
                manufacturer = details.manufacturer,
                model = details.model,
                size = details.size.orEmpty(),
                serialNumber = details.serialNumber.orEmpty(),
                certificationClass = details.certificationClass,
                condition = details.acquisition.condition,
                purchaseDate = details.acquisition.purchaseDate,
                priceAmount = price?.let { formatAmount(it.minorUnits) }.orEmpty(),
                priceCurrency = price?.currency?.value ?: DEFAULT_CURRENCY,
                inServiceDate = details.inServiceDate,
                hoursBeforeTracking = formatHoursAndMinutes(details.hoursBeforeTracking.inWholeMinutes),
                flightsBeforeTracking =
                    details.flightsBeforeTracking
                        .takeIf { it > 0 }
                        ?.toString()
                        .orEmpty(),
                intervalMonths =
                    details.maintenanceInterval
                        ?.months
                        ?.toString()
                        .orEmpty(),
                intervalHours =
                    details.maintenanceInterval
                        ?.hours
                        ?.let { formatHoursAndMinutes(it.inWholeMinutes) }
                        .orEmpty(),
            )
        }
    }
}

enum class WingFormField {
    Name,
    Manufacturer,
    Model,
    PriceAmount,
    PriceCurrency,
    HoursBeforeTracking,
    FlightsBeforeTracking,
    IntervalMonths,
    IntervalHours,
}

/** Error kinds rather than text, so the screen can show them in the user's language. */
enum class FormError {
    Required,
    InvalidAmount,
    InvalidCurrency,
    InvalidHours,
    InvalidCount,
    InvalidMonths,
    InvalidDays,
}

sealed interface WingFormResult {
    data class Valid(
        val details: WingDetails,
    ) : WingFormResult

    data class Invalid(
        val errors: Map<WingFormField, FormError>,
    ) : WingFormResult
}

fun WingFormInput.validate(): WingFormResult {
    val errors = linkedMapOf<WingFormField, FormError>()

    if (name.isBlank()) errors[WingFormField.Name] = FormError.Required
    if (manufacturer.isBlank()) errors[WingFormField.Manufacturer] = FormError.Required
    if (model.isBlank()) errors[WingFormField.Model] = FormError.Required

    val price = parsePrice(errors)
    val hours = parseHours(errors)
    val flightCount = parseFlightCount(errors)
    val interval = parseInterval(errors)

    if (errors.isNotEmpty() || hours == null || flightCount == null) return WingFormResult.Invalid(errors)

    return WingFormResult.Valid(
        WingDetails(
            name = name.trim(),
            manufacturer = manufacturer.trim(),
            model = model.trim(),
            size = size.trim().ifEmpty { null },
            serialNumber = serialNumber.trim().ifEmpty { null },
            certificationClass = certificationClass,
            acquisition = Acquisition(condition, purchaseDate, price),
            inServiceDate = inServiceDate,
            hoursBeforeTracking = hours,
            flightsBeforeTracking = flightCount,
            maintenanceInterval = interval,
        ),
    )
}

private fun WingFormInput.parsePrice(errors: MutableMap<WingFormField, FormError>): Price? =
    when (val parsed = parsePrice(priceAmount, priceCurrency)) {
        is PriceParse.Parsed -> {
            parsed.price
        }

        is PriceParse.Invalid -> {
            if (parsed.amountInvalid) errors[WingFormField.PriceAmount] = FormError.InvalidAmount
            if (parsed.currencyInvalid) errors[WingFormField.PriceCurrency] = FormError.InvalidCurrency
            null
        }
    }

private fun WingFormInput.parseHours(errors: MutableMap<WingFormField, FormError>): Duration? {
    if (hoursBeforeTracking.isBlank()) return Duration.ZERO
    val hours = parseHoursAndMinutes(hoursBeforeTracking)
    if (hours == null) errors[WingFormField.HoursBeforeTracking] = FormError.InvalidHours
    return hours
}

private fun WingFormInput.parseFlightCount(errors: MutableMap<WingFormField, FormError>): Int? {
    if (flightsBeforeTracking.isBlank()) return 0
    val count = flightsBeforeTracking.trim().takeIf { COUNT.matches(it) }?.toInt()
    if (count == null) errors[WingFormField.FlightsBeforeTracking] = FormError.InvalidCount
    return count
}

private val COUNT = Regex("""\d{1,6}""")

private val MONTHS = Regex("""\d{1,3}""")

// Both limits are optional, but at least one is needed for a rule; with neither there is no rule.
private fun WingFormInput.parseInterval(errors: MutableMap<WingFormField, FormError>): MaintenanceInterval? {
    val months =
        intervalMonths.trim().takeIf { it.isNotEmpty() }?.let { text ->
            text.takeIf { MONTHS.matches(it) }?.toInt()?.takeIf { it > 0 }
                ?: run {
                    errors[WingFormField.IntervalMonths] = FormError.InvalidMonths
                    null
                }
        }
    val hours =
        intervalHours.trim().takeIf { it.isNotEmpty() }?.let { text ->
            parseHoursAndMinutes(text)?.takeIf { it.isPositive() }
                ?: run {
                    errors[WingFormField.IntervalHours] = FormError.InvalidHours
                    null
                }
        }
    return if (months == null && hours == null) null else MaintenanceInterval(months, hours)
}
