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
    LoadMaintenance,
    SaveCheck,
    DeleteCheck,
    OpenReport,
    SavePlan,
    CancelPlan,
    LoadAttention,
    LoadSettings,
    SaveSettings,
    ExportCalendar,
    ImportIgc,
    OpenTrack,
    DropNoIgc,
    OpenDataFolder,
    ChangeDataFolder,
    DataFolderSame,
    DataFolderUnusable,
    DataFolderOverlaps,
    SyrideUserNotFound,
    SyrideUnreachable,
    SyridePagesChanged,
    ImportSyride,
}

/** A technical [detail] travels with the kind; the screen wraps it in a message in the user's language. */
data class Failure(
    val kind: FailureKind,
    val detail: String? = null,
)
