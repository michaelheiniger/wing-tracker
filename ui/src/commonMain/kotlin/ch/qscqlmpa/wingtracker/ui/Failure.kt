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
    ReadTrack,
    ReadGround,
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
    UpdateTakeOffTimes,
    SpreadsheetUnreadable,
    SpreadsheetOldFormat,
    ImportSpreadsheet,
    AirtimeTokenRefused,
    AirtimePilotNotFound,
    AirtimeUnreachable,
    AirtimeUnexpected,
    ExportAirtime,
    UnsupportedPath,

    // Shared by every feature that goes online, since the remedy is the same.
    NoConnection,
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

// The connection may be back a moment later. A refusal is left out: its message asks to wait.
private val WORTH_RETRYING =
    setOf(
        FailureKind.NoConnection,
        FailureKind.SyrideUnreachable,
        FailureKind.AirtimeUnreachable,
        FailureKind.UpdateUnreachable,
    )

/** Whether doing the same again soon may well succeed, so the screen offers to try again. */
val Failure.isWorthRetrying: Boolean get() = kind in WORTH_RETRYING

/** A path the system cannot name gets its own message, whatever was being done; anything else reports as [kind]. */
fun Exception.toFailure(kind: FailureKind): Failure =
    when (this) {
        is UnsupportedPathException -> Failure(FailureKind.UnsupportedPath, path)
        else -> Failure(kind, message)
    }
