package ch.qscqlmpa.wingtracker.git

import ch.qscqlmpa.wingtracker.sync.ConflictChoice
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookSync
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions
import ch.qscqlmpa.wingtracker.sync.SyncAuthor
import ch.qscqlmpa.wingtracker.sync.SyncConflict
import ch.qscqlmpa.wingtracker.sync.SyncException
import ch.qscqlmpa.wingtracker.sync.SyncProblem
import ch.qscqlmpa.wingtracker.sync.SyncRemote
import ch.qscqlmpa.wingtracker.sync.SyncReport
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.lib.EmptyProgressMonitor
import org.eclipse.jgit.lib.ProgressMonitor
import java.io.File
import java.nio.file.Path
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Synchronises the data folder as a Git repository (ADR 0033): every transaction becomes one commit, and a sync
 * rebases this device's commits onto the other devices' ones. One lock covers transactions, syncs and whatever
 * must not see half a change, so a sync never reads or rewrites files a use case is writing.
 */
class GitLogbook(
    dataDirectory: Path,
    private val layout: SyncedFolderLayout,
    // Bounds every network call, so a silent host never holds up starting or closing the app.
    private val timeout: Duration,
) : LogbookSync,
    LogbookTransactions {
    private val conflicts = GitConflicts(layout)
    private val folder = GitFolder(dataDirectory.toFile(), layout, conflicts, timeout.inWholeSeconds.toInt())
    private val lock = Mutex()

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) { folder.isRepository() }

    override suspend fun <T> run(
        change: LogbookChange,
        block: suspend () -> T,
    ): T {
        if (currentCoroutineContext()[InTransaction] != null) return block()
        return lock.withLock {
            var completed = false
            try {
                withContext(InTransaction()) { block() }.also { completed = true }
            } finally {
                // Nothing is rolled back, so what an interrupted transaction wrote is recorded all the same (ADR 0033).
                record(change.commitMessage(interrupted = !completed))
            }
        }
    }

    override suspend fun <T> whenIdle(block: suspend () -> T): T =
        if (currentCoroutineContext()[InTransaction] != null) block() else lock.withLock { block() }

    override suspend fun connect(
        remote: SyncRemote,
        author: SyncAuthor,
    ) = lock.withLock {
        online {
            if (!folder.isRemoteEmpty(remote)) throw SyncException(SyncProblem.RemoteNotEmpty)
            folder.openOrCreate().use { git ->
                git.configure(author)
                folder.writeIgnoreFile()
                folder.commitChanges(git, FIRST_COMMIT_MESSAGE)
                folder.push(git, remote, this)
            }
        }
    }

    // Writes to another folder only, so it runs beside transactions and syncs of the current one.
    override suspend fun join(
        remote: SyncRemote,
        author: SyncAuthor,
        folder: String,
    ) = online {
        GitFolder(File(folder), layout, conflicts, timeout.inWholeSeconds.toInt()).cloneFrom(remote, author, this)
    }

    override suspend fun sync(
        remote: SyncRemote,
        author: SyncAuthor,
        resolutions: Map<SyncedRecord, ConflictChoice>,
    ): SyncReport =
        lock.withLock {
            online {
                folder.open().use { git ->
                    git.configure(author)
                    folder.commitChanges(git, STRAY_CHANGES_MESSAGE)
                    folder.exchange(git, remote, resolutions, this)
                }
            }
        }

    override suspend fun conflicts(): List<SyncConflict> =
        lock.withLock {
            withContext(Dispatchers.IO) {
                if (folder.isRepository()) folder.open().use(conflicts::current) else emptyList()
            }
        }

    override suspend fun changeAuthor(author: SyncAuthor) =
        lock.withLock {
            withContext(Dispatchers.IO) {
                if (folder.isRepository()) folder.open().use { it.configure(author) }
            }
        }

    // A change is already on disk here: failing to commit it must not fail the use case, and the next sync commits it.
    private suspend fun record(message: String) =
        withContext(NonCancellable + Dispatchers.IO) {
            if (!folder.isRepository()) return@withContext
            try {
                folder.open().use { folder.commitChanges(it, message) }
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                logger.warn(e) { "Could not commit \"$message\"; the next sync commits it" }
            }
        }

    /** Runs JGit calls that may go online, cancelled with the coroutine and failing with a typed problem. */
    private suspend fun <T> online(action: ProgressMonitor.() -> T): T {
        val job = currentCoroutineContext()[Job]
        return withContext(Dispatchers.IO) {
            try {
                CancelledWith(job).action()
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                if (e is SyncException || e is CancellationException) throw e
                // JGit reports a cancel as a failure of its own.
                currentCoroutineContext().ensureActive()
                logger.warn(e) { "Synchronising failed" }
                throw SyncException(syncProblemOf(e), e.message, e)
            }
        }
    }

    // Marks the coroutines of a running transaction, so one started inside it joins it instead of waiting for the lock.
    private class InTransaction : AbstractCoroutineContextElement(InTransaction) {
        companion object Key : CoroutineContext.Key<InTransaction>
    }
}

private class CancelledWith(
    private val job: Job?,
) : EmptyProgressMonitor() {
    override fun isCancelled() = job?.isActive == false
}
