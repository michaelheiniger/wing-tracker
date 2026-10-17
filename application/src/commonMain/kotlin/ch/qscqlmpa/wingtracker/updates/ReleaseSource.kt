package ch.qscqlmpa.wingtracker.updates

enum class ReleaseProblem {
    // Offline, a timeout or a server error: worth trying again later.
    Unreachable,

    // The server declined, e.g. too many checks from this network for now.
    Refused,

    // The answer could not be read; the release source changed or something sits in between.
    Unexpected,
}

class ReleaseSourceException(
    val problem: ReleaseProblem,
    detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail ?: problem.name, cause)

/** Where published releases of the application are listed (ADR 0024). */
interface ReleaseSource {
    /**
     * The published releases, without drafts and pre-releases; their order does not matter.
     * @throws ReleaseSourceException when the list cannot be had.
     */
    suspend fun published(): List<Release>
}
