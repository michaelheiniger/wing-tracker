package ch.qscqlmpa.wingtracker.ui

import ch.qscqlmpa.wingtracker.ui.platform.UnsupportedPathException

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
    NoIgcInFolder,
    OpenDataFolder,
    OpenLogFolder,
    ChangeDataFolder,
    DataFolderSame,
    DataFolderUnusable,
    DataFolderOverlaps,
    SyrideUserNotFound,
    SyrideUnreachable,
    SyridePagesChanged,
    ImportSyride,
    SpreadsheetUnreadable,
    SpreadsheetOldFormat,
    ImportSpreadsheet,
    UnsupportedPath,
    UpdateUnreachable,
    UpdateRefused,
    UpdateUnexpected,
    UpdateNoChecksum,
    UpdateChecksumMismatch,
    InstallUpdate,
}

/** A technical [detail] travels with the kind; the screen wraps it in a message in the user's language. */
data class Failure(
    val kind: FailureKind,
    val detail: String? = null,
)

/** A path the system cannot name gets its own message, whatever was being done; anything else reports as [kind]. */
fun Exception.toFailure(kind: FailureKind): Failure =
    when (this) {
        is UnsupportedPathException -> Failure(FailureKind.UnsupportedPath, path)
        else -> Failure(kind, message)
    }
