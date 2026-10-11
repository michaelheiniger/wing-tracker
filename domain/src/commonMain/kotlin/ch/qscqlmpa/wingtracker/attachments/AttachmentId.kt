package ch.qscqlmpa.wingtracker.attachments

/** The pattern keeps ids safe to use as file names in storage adapters. */
@JvmInline
value class AttachmentId(
    val value: String,
) {
    init {
        require(PATTERN.matches(value)) { "Invalid attachment id '$value'" }
    }

    private companion object {
        val PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
