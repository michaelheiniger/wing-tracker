package ch.qscqlmpa.wingtracker.updates

/** The SHA-256 checksums of a release's files, as the `sha256sum` tool writes them: one "checksum  name" per line. */
class Checksums private constructor(
    private val byName: Map<String, String>,
) {
    /** The checksum of [fileName] in lowercase hexadecimal, or null when the file is not listed. */
    fun of(fileName: String): String? = byName[fileName]

    /** True when [actual] is the listed checksum of [fileName]; a file that is not listed never matches. */
    fun matches(
        fileName: String,
        actual: String,
    ): Boolean = of(fileName)?.equals(actual, ignoreCase = true) == true

    companion object {
        // A space then either a second space (text mode) or an asterisk (binary mode) separate checksum and name.
        private val LINE = Regex("([0-9a-fA-F]{64}) [ *](.+)")

        /** Lines that are not a checksum are ignored, so a stray blank line or comment does not spoil the file. */
        fun parse(text: String): Checksums =
            Checksums(
                text
                    .lineSequence()
                    .mapNotNull { LINE.matchEntire(it.trimEnd('\r')) }
                    .associate { it.groupValues[2] to it.groupValues[1].lowercase() },
            )
    }
}
