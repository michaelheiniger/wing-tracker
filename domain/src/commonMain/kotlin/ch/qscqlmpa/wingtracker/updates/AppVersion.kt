package ch.qscqlmpa.wingtracker.updates

/** A released version of the application: strict MAJOR.MINOR.PATCH (ADR 0009), compared numerically. */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {
    init {
        require(major >= 0 && minor >= 0 && patch >= 0) { "Version parts cannot be negative: $major.$minor.$patch" }
    }

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString() = "$major.$minor.$patch"

    companion object {
        // Release tags carry a leading "v" (ADR 0009); anything else, such as a development build, is no release.
        private val PATTERN = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)")

        /** Null when [text] is not a release version. */
        fun parseOrNull(text: String): AppVersion? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            val (major, minor, patch) = match.destructured.toList().map { it.toIntOrNull() ?: return null }
            return AppVersion(major, minor, patch)
        }
    }
}
