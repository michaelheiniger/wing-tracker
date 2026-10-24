package ch.qscqlmpa.wingtracker.git

import ch.qscqlmpa.wingtracker.sync.SyncAuthor
import ch.qscqlmpa.wingtracker.sync.SyncException
import ch.qscqlmpa.wingtracker.sync.SyncProblem
import ch.qscqlmpa.wingtracker.sync.SyncRemote
import ch.qscqlmpa.wingtracker.sync.SyncReport
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.util.io.DisabledOutputStream
import java.io.File

private const val BRANCH = "main"
private const val BRANCH_REF = "${Constants.R_HEADS}$BRANCH"

// Fetched under a name of its own: the remote's address is a device setting, not a remote configured in Git.
private const val FETCHED_PREFIX = "refs/remotes/sync/"
private const val IGNORE_FILE = ".gitignore"

// Hosts that ignore the username still need one in the request.
private const val ANY_USERNAME = "wing-tracker"

/** The Git mechanics of the synchronised folder; [GitLogbook] puts the ports and their lock around them. */
internal class GitFolder(
    private val folder: File,
    private val layout: SyncedFolderLayout,
) {
    fun isRepository() = File(folder, Constants.DOT_GIT).isDirectory

    fun open(): Git = Git.open(folder)

    /** A repository left by a connect that failed to push is used again. */
    fun openOrCreate(): Git =
        if (isRepository()) {
            open()
        } else {
            Git
                .init()
                .setDirectory(folder)
                .setInitialBranch(BRANCH)
                .call()
        }

    /** Copies the logbook [remote] holds into this folder, which must be empty. */
    fun cloneFrom(
        remote: SyncRemote,
        author: SyncAuthor,
        monitor: ProgressMonitor,
    ) {
        Git
            .cloneRepository()
            .setURI(remote.url)
            .setDirectory(folder)
            .setBranch(BRANCH)
            .setCredentialsProvider(remote.credentials())
            .setProgressMonitor(monitor)
            .call()
            .use { it.configure(author) }
    }

    fun isRemoteEmpty(remote: SyncRemote): Boolean =
        Git
            .lsRemoteRepository()
            .setRemote(remote.url)
            .setCredentialsProvider(remote.credentials())
            .setHeads(true)
            .call()
            .isEmpty()

    fun writeIgnoreFile() {
        val lines = listOf("# Kept on each device, never synchronised.") + layout.deviceOnly
        val content = lines.joinToString("\n", postfix = "\n")
        val file = File(folder, IGNORE_FILE)
        if (!file.exists() || file.readText() != content) file.writeText(content)
    }

    /**
     * Commits whatever changed since the last commit; returns whether there was anything. A repository without history
     * always gets a first commit, since there is nothing to push otherwise.
     */
    fun commitChanges(
        git: Git,
        message: String,
    ): Boolean {
        // Stages deletions too, like `git add --all`.
        git.add().addFilepattern(".").call()
        val status = git.status().call()
        // A quarantined record is missing but not deleted (ADR 0015): its absence is never pushed to other devices.
        status.removed.filter(layout::isQuarantined).forEach { git.reset().addPath(it).call() }
        status.missing.filterNot(layout::isQuarantined).forEach {
            git
                .rm()
                .setCached(true)
                .addFilepattern(it)
                .call()
        }
        val staged = git.status().call().run { added + changed + removed }
        val first = git.repository.resolve(Constants.HEAD) == null
        if (staged.isEmpty() && !first) return false
        git
            .commit()
            .setMessage(message)
            .setAllowEmpty(first)
            .call()
        return true
    }

    /** Takes the other devices' commits and sends this device's, unless a record changed on both sides. */
    fun exchange(
        git: Git,
        remote: SyncRemote,
        monitor: ProgressMonitor,
    ): SyncReport {
        git
            .fetch()
            .setRemote(remote.url)
            .setRefSpecs(RefSpec("+${Constants.R_HEADS}*:$FETCHED_PREFIX*"))
            .setCredentialsProvider(remote.credentials())
            .setProgressMonitor(monitor)
            .call()
        val local = checkNotNull(git.repository.resolve(BRANCH_REF)) { "The logbook has no commit" }
        val fetched = git.repository.resolve("$FETCHED_PREFIX$BRANCH")
        return if (fetched == null) {
            // Emptied since connecting, e.g. recreated by its owner: it gets the whole logbook again.
            push(git, remote, monitor)
            SyncReport(received = 0, sent = git.count(local, until = null))
        } else {
            joinWith(git, local, fetched, remote, monitor)
        }
    }

    fun push(
        git: Git,
        remote: SyncRemote,
        monitor: ProgressMonitor,
    ) {
        val results =
            git
                .push()
                .setRemote(remote.url)
                .setRefSpecs(RefSpec("$BRANCH_REF:$BRANCH_REF"))
                .setCredentialsProvider(remote.credentials())
                .setProgressMonitor(monitor)
                .call()
        val refused =
            results.flatMap { it.remoteUpdates }.filter {
                it.status != RemoteRefUpdate.Status.OK && it.status != RemoteRefUpdate.Status.UP_TO_DATE
            }
        // Another device pushed between this fetch and push; the next sync takes its commits first.
        if (refused.isNotEmpty()) {
            throw SyncException(
                SyncProblem.Unexpected,
                "Push refused: ${refused.map { it.status }}",
            )
        }
    }

    private fun joinWith(
        git: Git,
        local: ObjectId,
        fetched: ObjectId,
        remote: SyncRemote,
        monitor: ProgressMonitor,
    ): SyncReport {
        val base =
            git.mergeBase(local, fetched)
                ?: throw SyncException(SyncProblem.Unexpected, "The repository holds another logbook")
        // A record changed on both sides is a conflict even when Git could merge its lines (ADR 0033).
        val conflicts = git.changedPaths(base, local) intersect git.changedPaths(base, fetched)
        if (conflicts.isNotEmpty()) return SyncReport(0, 0, conflicts.sorted().map(layout::recordAt))
        val received = git.count(fetched, until = base)
        val sent = git.count(local, until = base)
        if (received > 0) integrate(git, fetched, rebase = sent > 0)
        if (sent > 0) push(git, remote, monitor)
        return SyncReport(received, sent)
    }

    /** Takes the other devices' commits, replaying this device's on top so the history stays a straight line. */
    private fun integrate(
        git: Git,
        fetched: ObjectId,
        rebase: Boolean,
    ) {
        if (!rebase) {
            val result =
                git
                    .merge()
                    .include(fetched)
                    .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
                    .call()
            if (!result.mergeStatus.isSuccessful) throw SyncException(SyncProblem.Unexpected, "${result.mergeStatus}")
            return
        }
        val result = git.rebase().setUpstream(fetched).call()
        if (!result.status.isSuccessful) {
            // Records changed on both sides were caught before, so this is unexpected: nothing is left half-done.
            git.rebase().setOperation(RebaseCommand.Operation.ABORT).call()
            throw SyncException(SyncProblem.Unexpected, "Rebase stopped: ${result.status}")
        }
    }
}

internal fun Git.configure(author: SyncAuthor) {
    repository.config.apply {
        setString("user", null, "name", author.name)
        setString("user", null, "email", author.email)
        // Files are committed byte for byte, so every device reads them exactly as they were written.
        setBoolean("core", null, "autocrlf", false)
        save()
    }
}

private fun SyncRemote.credentials(): CredentialsProvider =
    UsernamePasswordCredentialsProvider(username?.takeIf { it.isNotBlank() } ?: ANY_USERNAME, token)

private fun Git.mergeBase(
    a: ObjectId,
    b: ObjectId,
): ObjectId? =
    RevWalk(repository).use { walk ->
        walk.revFilter = RevFilter.MERGE_BASE
        walk.markStart(walk.parseCommit(a))
        walk.markStart(walk.parseCommit(b))
        walk.next()?.id
    }

private fun Git.count(
    from: ObjectId,
    until: ObjectId?,
): Int =
    RevWalk(repository).use { walk ->
        walk.markStart(walk.parseCommit(from))
        until?.let { walk.markUninteresting(walk.parseCommit(it)) }
        walk.count()
    }

private fun Git.changedPaths(
    from: ObjectId,
    to: ObjectId,
): Set<String> =
    RevWalk(repository).use { walk ->
        DiffFormatter(DisabledOutputStream.INSTANCE).use { diff ->
            diff.setRepository(repository)
            diff
                .scan(walk.parseCommit(from).tree, walk.parseCommit(to).tree)
                .flatMap { listOf(it.oldPath, it.newPath) }
                .filter { it != DiffEntry.DEV_NULL }
                .toSet()
        }
    }
