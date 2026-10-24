package ch.qscqlmpa.wingtracker.git

import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookSync
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions
import ch.qscqlmpa.wingtracker.sync.SyncAuthor
import ch.qscqlmpa.wingtracker.sync.SyncException
import ch.qscqlmpa.wingtracker.sync.SyncProblem
import ch.qscqlmpa.wingtracker.sync.SyncRemote
import ch.qscqlmpa.wingtracker.sync.SyncReport
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
import java.nio.file.Path
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private val logger = KotlinLogging.logger {}

/**
 * Synchronises the data folder as a Git repository (ADR 0033): every transaction becomes one commit, and a sync
 * rebases this device's commits onto the other devices' ones. One lock covers transactions, syncs and whatever
 * must not see half a change, so a sync never reads or rewrites files a use case is writing.
 */
class GitLogbook(
    dataDirectory: Path,
    layout: SyncedFolderLayout,
) : LogbookSync,
    LogbookTransactions {
    private val folder = GitFolder(dataDirectory.toFile(), layout)
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
                folder.configure(git, author)
                folder.writeIgnoreFile()
                folder.commitChanges(git, FIRST_COMMIT_MESSAGE)
                folder.push(git, remote, this)
            }
        }
    }

    override suspend fun sync(
        remote: SyncRemote,
        author: SyncAuthor,
    ): SyncReport =
        lock.withLock {
            online {
                folder.open().use { git ->
                    folder.configure(git, author)
                    folder.commitChanges(git, STRAY_CHANGES_MESSAGE)
                    folder.exchange(git, remote, this)
                }
            }
        }

    override suspend fun changeAuthor(author: SyncAuthor) =
        lock.withLock {
            withContext(Dispatchers.IO) {
                if (folder.isRepository()) folder.open().use { folder.configure(it, author) }
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
