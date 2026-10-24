package ch.qscqlmpa.wingtracker.sync

/** Exchanges the logbook's changes with a remote repository, so every device holds the same logbook (ADR 0033). */
interface LogbookSync {
    /** Whether the data folder already holds a synchronised logbook. */
    suspend fun isConnected(): Boolean

    /**
     * Starts synchronising the logbook to [remote], which must be empty: everything stored so far is sent as the
     * first change. A remote that already holds something is refused, since joining two logbooks is not supported.
     */
    suspend fun connect(
        remote: SyncRemote,
        author: SyncAuthor,
    )

    /**
     * Copies the logbook [remote] holds into the empty [folder], for this device to use from the next start (ADR 0020).
     * The current data folder is not touched.
     */
    suspend fun join(
        remote: SyncRemote,
        author: SyncAuthor,
        folder: String,
    )

    /**
     * Receives the changes of other devices and sends this device's. When a record changed on both sides since the
     * last sync, nothing is exchanged and the report names the records; resolving them comes later.
     */
    suspend fun sync(
        remote: SyncRemote,
        author: SyncAuthor,
    ): SyncReport

    /** Signs this device's next changes with [author]. */
    suspend fun changeAuthor(author: SyncAuthor)
}

data class SyncRemote(
    val url: String,
    val username: String?,
    val token: String,
)

data class SyncAuthor(
    val name: String,
    val email: String,
)

/** How many changes came from other devices and went to them; [conflicts] is empty when the sync went through. */
data class SyncReport(
    val received: Int,
    val sent: Int,
    val conflicts: List<SyncedRecord> = emptyList(),
)

/** A record of the logbook as synchronising sees it; the UI words it in the pilot's language. */
data class SyncedRecord(
    val kind: RecordKind,
    val id: String,
)

enum class RecordKind { Wing, Harness, Reserve, Flight, Attachment, LogbookSettings, Other }

enum class SyncProblem {
    NoConnection,

    // Connected, but the answer did not come.
    Unreachable,
    CredentialsRefused,
    NoSuchRepository,

    // Connecting is only possible to an empty repository; one that holds a logbook is joined instead.
    RemoteNotEmpty,
    Unexpected,
}

class SyncException(
    val problem: SyncProblem,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: problem.name, cause)
