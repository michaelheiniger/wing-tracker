package ch.qscqlmpa.wingtracker.gear.wing

/** The pattern keeps ids safe to use as file names in storage adapters. */
@JvmInline
value class WingId(
    val value: String,
) {
    init {
        require(PATTERN.matches(value)) { "Invalid wing id '$value'" }
    }

    private companion object {
        val PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
