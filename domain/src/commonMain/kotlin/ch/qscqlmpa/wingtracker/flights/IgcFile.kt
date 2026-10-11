package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.attachments.Attachment

/**
 * The original track a flight was imported from, kept unmodified (ADR 0011). The [checksum] identifies the exact
 * content, so importing the same file again can be recognised.
 */
data class IgcFile(
    val attachment: Attachment,
    val checksum: String,
) {
    init {
        require(checksum.isNotBlank()) { "An IGC file needs a checksum" }
    }
}
