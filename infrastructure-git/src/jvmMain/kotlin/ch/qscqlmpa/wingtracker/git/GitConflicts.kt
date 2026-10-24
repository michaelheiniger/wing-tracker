package ch.qscqlmpa.wingtracker.git

import ch.qscqlmpa.wingtracker.sync.ConflictChoice
import ch.qscqlmpa.wingtracker.sync.ConflictSide
import ch.qscqlmpa.wingtracker.sync.RecordChange
import ch.qscqlmpa.wingtracker.sync.SyncConflict
import ch.qscqlmpa.wingtracker.sync.SyncException
import ch.qscqlmpa.wingtracker.sync.SyncProblem
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.util.io.DisabledOutputStream
import java.io.File
import kotlin.time.Instant

/** Records changed on both sides of a sync (ADR 0033): finding them, showing both versions, keeping the chosen one. */
internal class GitConflicts(
    private val layout: SyncedFolderLayout,
) {
    /** A record changed on both sides is a conflict even when Git could merge its lines. */
    fun paths(
        git: Git,
        base: ObjectId,
        local: ObjectId,
        fetched: ObjectId,
    ): Set<String> = git.changedPaths(base, local) intersect git.changedPaths(base, fetched)

    /** The conflicts the last fetch left, with both versions; none when there was no fetch or nothing collides. */
    fun current(git: Git): List<SyncConflict> {
        val local = git.repository.resolve(BRANCH_REF) ?: return emptyList()
        val fetched = git.repository.resolve(FETCHED_BRANCH_REF) ?: return emptyList()
        val base = git.mergeBase(local, fetched) ?: return emptyList()
        return paths(git, base, local, fetched).sorted().map { path ->
            val here = git.blobAt(local, path)
            val there = git.blobAt(fetched, path)
            SyncConflict(
                record = layout.recordAt(path),
                summary = (here ?: there)?.let { layout.summary(path, it) },
                here = ConflictSide(git.lastChange(base, local, path), deleted = here == null),
                there = ConflictSide(git.lastChange(base, fetched, path), deleted = there == null),
                differences = layout.differences(path, here, there),
            )
        }
    }

    /**
     * Joins both sides in one merge commit holding the chosen version of every path in [paths]; a rebase would meet
     * the same conflict again in every commit that touched them. Returns false, changing nothing, when a path has no
     * choice in [resolutions].
     */
    fun resolve(
        git: Git,
        local: ObjectId,
        fetched: ObjectId,
        paths: Set<String>,
        resolutions: Map<SyncedRecord, ConflictChoice>,
    ): Boolean {
        val choices = paths.associateWith { resolutions[layout.recordAt(it)] }
        if (choices.values.any { it == null }) return false
        try {
            merge(git, fetched)
            choices.forEach { (path, choice) -> keep(git, path, if (choice == ConflictChoice.Here) local else fetched) }
            git.commit().setMessage("Keep the chosen version of ${paths.size} records changed on two devices").call()
        } catch (e: SyncException) {
            abandon(git, local)
            throw e
        }
        return true
    }

    private fun merge(
        git: Git,
        fetched: ObjectId,
    ) {
        val result =
            git
                .merge()
                .include(fetched)
                .setCommit(false)
                .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                .setStrategy(MergeStrategy.RECURSIVE)
                .call()
        // Conflicting is expected: those are the paths about to get their chosen version.
        val merging =
            result.mergeStatus == MergeResult.MergeStatus.MERGED_NOT_COMMITTED ||
                result.mergeStatus == MergeResult.MergeStatus.CONFLICTING
        if (!merging) throw SyncException(SyncProblem.Unexpected, "Merge stopped: ${result.mergeStatus}")
    }

    private fun keep(
        git: Git,
        path: String,
        version: ObjectId,
    ) {
        val file = File(git.repository.workTree, path)
        val bytes = git.blobAt(version, path)
        if (bytes == null) {
            file.delete()
            git.rm().addFilepattern(path).call()
        } else {
            file.parentFile.mkdirs()
            file.writeBytes(bytes)
            git.add().addFilepattern(path).call()
        }
    }

    // Everything was committed before the merge, so going back to that commit loses nothing.
    private fun abandon(
        git: Git,
        local: ObjectId,
    ) {
        git
            .reset()
            .setMode(ResetCommand.ResetType.HARD)
            .setRef(local.name)
            .call()
    }
}

internal fun Git.mergeBase(
    a: ObjectId,
    b: ObjectId,
): ObjectId? =
    RevWalk(repository).use { walk ->
        walk.revFilter = org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE
        walk.markStart(walk.parseCommit(a))
        walk.markStart(walk.parseCommit(b))
        walk.next()?.id
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

/** The file at [path] in [commit], or null where it does not exist there. */
private fun Git.blobAt(
    commit: ObjectId,
    path: String,
): ByteArray? =
    RevWalk(repository).use { walk ->
        TreeWalk.forPath(repository, path, walk.parseCommit(commit).tree)?.use {
            repository.open(it.getObjectId(0)).bytes
        }
    }

/** The last commit between [base] and [tip] that changed [path]. */
private fun Git.lastChange(
    base: ObjectId,
    tip: ObjectId,
    path: String,
): RecordChange? =
    log()
        .addRange(base, tip)
        .addPath(path)
        .setMaxCount(1)
        .call()
        .firstOrNull()
        ?.let {
            RecordChange(
                author = it.authorIdent.name,
                at = Instant.fromEpochMilliseconds(it.authorIdent.whenAsInstant.toEpochMilli()),
                description = it.shortMessage,
            )
        }
