package ch.qscqlmpa.wingtracker.ui.gear

import ch.qscqlmpa.wingtracker.gear.CurrencyCode
import ch.qscqlmpa.wingtracker.gear.Price

/** Pre-filled because the pilot buys in Swiss francs most of the time; it stays editable per item. */
const val DEFAULT_CURRENCY = "CHF"

private val AMOUNT = Regex("""(\d+)(?:[.,](\d{1,2}))?""")
private val CURRENCY = Regex("[A-Z]{3}")
private const val MINOR_UNITS_PER_MAJOR = 100

sealed interface PriceParse {
    data class Parsed(
        val price: Price?,
    ) : PriceParse

    data class Invalid(
        val amountInvalid: Boolean,
        val currencyInvalid: Boolean,
    ) : PriceParse
}

/** The currency is only looked at when an amount is given, because it is pre-filled and so never "empty". */
fun parsePrice(
    amount: String,
    currency: String,
): PriceParse {
    if (amount.isBlank()) return PriceParse.Parsed(null)
    val match = AMOUNT.matchEntire(amount.trim())
    val code = currency.trim().uppercase()
    val currencyValid = CURRENCY.matches(code)
    if (match == null || !currencyValid) return PriceParse.Invalid(match == null, !currencyValid)
    val (whole, cents) = match.destructured
    val minorUnits = whole.toLong() * MINOR_UNITS_PER_MAJOR + cents.padEnd(2, '0').toLong()
    return PriceParse.Parsed(Price(minorUnits, CurrencyCode(code)))
}

fun formatAmount(minorUnits: Long): String {
    val cents = (minorUnits % MINOR_UNITS_PER_MAJOR).toString().padStart(2, '0')
    return "${minorUnits / MINOR_UNITS_PER_MAJOR}.$cents"
}
