package ch.qscqlmpa.wingtracker.gear

/** Minor units avoid floating-point rounding errors in money. */
data class Price(
    val minorUnits: Long,
    val currency: CurrencyCode,
) {
    init {
        require(minorUnits >= 0) { "A price cannot be negative" }
    }
}
