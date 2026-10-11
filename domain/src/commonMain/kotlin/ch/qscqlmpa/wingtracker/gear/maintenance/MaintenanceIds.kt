package ch.qscqlmpa.wingtracker.gear.maintenance

private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

@JvmInline
value class CheckId(
    val value: String,
) {
    init {
        require(ID_PATTERN.matches(value)) { "Invalid check id '$value'" }
    }
}

@JvmInline
value class PlannedCheckId(
    val value: String,
) {
    init {
        require(ID_PATTERN.matches(value)) { "Invalid planned check id '$value'" }
    }
}
