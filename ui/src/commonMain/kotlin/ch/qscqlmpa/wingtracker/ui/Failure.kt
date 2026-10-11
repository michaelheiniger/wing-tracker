package ch.qscqlmpa.wingtracker.ui

enum class FailureKind {
    Load,
    Save,
    Retire,
    Delete,
    DeleteInUse,
    LoadLogbook,
    SaveFlight,
    DeleteFlight,
    LoadStatistics,
}

/** A technical [detail] travels with the kind; the screen wraps it in a message in the user's language. */
data class Failure(
    val kind: FailureKind,
    val detail: String? = null,
)
