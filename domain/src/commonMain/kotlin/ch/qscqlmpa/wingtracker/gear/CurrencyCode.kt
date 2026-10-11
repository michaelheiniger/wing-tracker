package ch.qscqlmpa.wingtracker.gear

/** ISO 4217 alphabetic code; chosen per purchase because gear is often bought abroad. */
@JvmInline
value class CurrencyCode(
    val value: String,
) {
    init {
        require(PATTERN.matches(value)) { "Currency code must be three uppercase letters but was '$value'" }
    }

    private companion object {
        val PATTERN = Regex("[A-Z]{3}")
    }
}
