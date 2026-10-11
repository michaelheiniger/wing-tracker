package ch.qscqlmpa.wingtracker.ui.gear.wing

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.CurrencyCode
import ch.qscqlmpa.wingtracker.gear.Price
import ch.qscqlmpa.wingtracker.gear.wing.CertificationClass
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.ui.formatHoursAndMinutes
import ch.qscqlmpa.wingtracker.ui.parseHoursAndMinutes
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/** Pre-filled because the pilot buys in Swiss francs most of the time; it stays editable per wing. */
const val DEFAULT_CURRENCY = "CHF"

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
    val intervalMonths: String = "",
    val intervalHours: String = "",
) {
    companion object {
        fun from(wing: Wing): WingFormInput {
            val details = wing.details
            val price = details.acquisition.price
            return WingFormInput(
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
    InServiceDate,
    PriceAmount,
    PriceCurrency,
    HoursBeforeTracking,
    IntervalMonths,
    IntervalHours,
}

/** Error kinds rather than text, so the screen can show them in the user's language. */
enum class WingFormError {
    Required,
    InvalidAmount,
    InvalidCurrency,
    InvalidHours,
    InvalidMonths,
}

sealed interface WingFormResult {
    data class Valid(
        val details: WingDetails,
    ) : WingFormResult

    data class Invalid(
        val errors: Map<WingFormField, WingFormError>,
    ) : WingFormResult
}

fun WingFormInput.validate(): WingFormResult {
    val errors = linkedMapOf<WingFormField, WingFormError>()

    if (name.isBlank()) errors[WingFormField.Name] = WingFormError.Required
    if (manufacturer.isBlank()) errors[WingFormField.Manufacturer] = WingFormError.Required
    if (model.isBlank()) errors[WingFormField.Model] = WingFormError.Required
    if (inServiceDate == null) errors[WingFormField.InServiceDate] = WingFormError.Required

    val price = parsePrice(errors)
    val hours = parseHours(errors)
    val interval = parseInterval(errors)

    if (errors.isNotEmpty() || inServiceDate == null || hours == null) return WingFormResult.Invalid(errors)

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
            maintenanceInterval = interval,
        ),
    )
}

private val AMOUNT = Regex("""(\d+)(?:[.,](\d{1,2}))?""")
private val CURRENCY = Regex("[A-Z]{3}")
private const val MINOR_UNITS_PER_MAJOR = 100

// The currency is only looked at when an amount is given, because it is pre-filled and so never "empty".
private fun WingFormInput.parsePrice(errors: MutableMap<WingFormField, WingFormError>): Price? {
    if (priceAmount.isBlank()) return null
    val match = AMOUNT.matchEntire(priceAmount.trim())
    val currency = priceCurrency.trim().uppercase()
    if (match == null) errors[WingFormField.PriceAmount] = WingFormError.InvalidAmount
    if (!CURRENCY.matches(currency)) errors[WingFormField.PriceCurrency] = WingFormError.InvalidCurrency
    if (match == null || !CURRENCY.matches(currency)) return null

    val (whole, cents) = match.destructured
    val minorUnits = whole.toLong() * MINOR_UNITS_PER_MAJOR + cents.padEnd(2, '0').toLong()
    return Price(minorUnits, CurrencyCode(currency))
}

private fun WingFormInput.parseHours(errors: MutableMap<WingFormField, WingFormError>): Duration? {
    if (hoursBeforeTracking.isBlank()) return Duration.ZERO
    val hours = parseHoursAndMinutes(hoursBeforeTracking)
    if (hours == null) errors[WingFormField.HoursBeforeTracking] = WingFormError.InvalidHours
    return hours
}

private val MONTHS = Regex("""\d{1,3}""")

// Both limits are optional, but at least one is needed for a rule; with neither there is no rule.
private fun WingFormInput.parseInterval(errors: MutableMap<WingFormField, WingFormError>): MaintenanceInterval? {
    val months =
        intervalMonths.trim().takeIf { it.isNotEmpty() }?.let { text ->
            text.takeIf { MONTHS.matches(it) }?.toInt()?.takeIf { it > 0 }
                ?: run {
                    errors[WingFormField.IntervalMonths] = WingFormError.InvalidMonths
                    null
                }
        }
    val hours =
        intervalHours.trim().takeIf { it.isNotEmpty() }?.let { text ->
            parseHoursAndMinutes(text)?.takeIf { it.isPositive() }
                ?: run {
                    errors[WingFormField.IntervalHours] = WingFormError.InvalidHours
                    null
                }
        }
    return if (months == null && hours == null) null else MaintenanceInterval(months, hours)
}

private fun formatAmount(minorUnits: Long): String {
    val cents = (minorUnits % MINOR_UNITS_PER_MAJOR).toString().padStart(2, '0')
    return "${minorUnits / MINOR_UNITS_PER_MAJOR}.$cents"
}
