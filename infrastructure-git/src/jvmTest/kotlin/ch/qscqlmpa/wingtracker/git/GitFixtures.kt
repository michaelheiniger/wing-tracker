package ch.qscqlmpa.wingtracker.git

import ch.qscqlmpa.wingtracker.sync.FieldDifference
import ch.qscqlmpa.wingtracker.sync.RecordKind
import ch.qscqlmpa.wingtracker.sync.RecordSummary
import ch.qscqlmpa.wingtracker.sync.SyncAuthor
import ch.qscqlmpa.wingtracker.sync.SyncRemote
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File
import java.nio.file.Path

val PILOT = SyncAuthor("Michael Heiniger", "michael@example.org")
val OTHER_DEVICE = SyncAuthor("Michael Heiniger (desktop)", "michael@example.org")

/** The storage adapter's layout, reduced to what these tests write. */
class TestLayout(
    private val folder: Path,
) : SyncedFolderLayout {
    override val deviceOnly = listOf("/quarantine/", "*.tmp", "/device-settings.json")

    override fun isQuarantined(path: String) =
        folder
            .resolve("quarantine")
            .resolve(path)
            .toFile()
            .exists()

    override fun recordAt(path: String): SyncedRecord {
        val (collection, file) = path.split('/')
        val kind = if (collection == "flights") RecordKind.Flight else RecordKind.Wing
        return SyncedRecord(kind, file.removeSuffix(".json"))
    }

    override fun summary(
        path: String,
        version: ByteArray,
    ) = RecordSummary.Gear(path)

    // The whole file as one field: enough to tell the two versions apart here.
    override fun differences(
        path: String,
        here: ByteArray?,
        there: ByteArray?,
    ) = listOf(FieldDifference("content", here?.decodeToString(), there?.decodeToString()))
}

/** An empty repository on this computer, standing in for the pilot's host. */
fun aRemote(directory: Path): SyncRemote {
    Git
        .init()
        .setBare(true)
        .setDirectory(directory.toFile())
        .setInitialBranch("main")
        .call()
        .close()
    return SyncRemote(directory.toUri().toString(), username = "michael", token = "ghp_exampletoken")
}

fun Path.store(
    path: String,
    content: String,
) {
    val file = resolve(path).toFile()
    file.parentFile.mkdirs()
    file.writeText(content)
}

fun aFlightDocument(
    duration: Int = 95,
    site: String = "Niederhorn",
) = """{"schemaVersion": 5, "date": "2026-09-20", "durationMinutes": $duration, "takeOffSite": "$site"}"""

/** Another device of the pilot: a clone that changes the logbook and sends it. */
class OtherDevice(
    remote: SyncRemote,
    private val directory: Path,
) {
    init {
        clone(remote, directory)
    }

    fun deleteAndPush(path: String) =
        withGit(directory) { git ->
            directory.resolve(path).toFile().delete()
            git.rm().addFilepattern(path).call()
            commitAndPush(git, "Delete from the other device")
        }

    fun commitAndPush(
        path: String,
        content: String,
        message: String = "Log flight from the other device",
    ) = withGit(directory) { git ->
        directory.store(path, content)
        git.add().addFilepattern(".").call()
        commitAndPush(git, message)
    }

    private fun commitAndPush(
        git: Git,
        message: String,
    ) {
        git
            .commit()
            .setMessage(message)
            .setAuthor(OTHER_DEVICE.name, OTHER_DEVICE.email)
            .call()
        // A clone of an empty repository starts on JGit's default branch, so the branch is named.
        git
            .push()
            .setRefSpecs(RefSpec("HEAD:refs/heads/main"))
            .call()
    }
}

/** What the remote holds, read as a fresh clone would see it. */
class RemoteContent(
    remote: SyncRemote,
    private val directory: Path,
) {
    init {
        clone(remote, directory)
    }

    fun files(): List<String> = withGit(directory) { it.repository.filesAt(Constants.HEAD) }

    fun read(path: String): String = directory.resolve(path).toFile().readText()

    fun commits(): List<RevCommit> = withGit(directory) { it.log().call().toList() }
}

private fun clone(
    remote: SyncRemote,
    directory: Path,
) = Git
    .cloneRepository()
    .setURI(remote.url)
    .setDirectory(directory.toFile())
    .setBranch("main")
    .call()
    .close()

// Opened per use and closed at once: Windows cannot delete a test's folder while a repository in it is still open.
private fun <T> withGit(
    directory: Path,
    action: (Git) -> T,
): T = Git.open(directory.toFile()).use(action)

fun org.eclipse.jgit.lib.Repository.filesAt(revision: String): List<String> {
    val tree = parseCommit(resolve(revision)).tree
    return TreeWalk(this).use { walk ->
        walk.addTree(tree)
        walk.isRecursive = true
        buildList { while (walk.next()) add(walk.pathString) }
    }
}

fun aWingRef(id: String = "w-1") =
    ch.qscqlmpa.wingtracker.gear.maintenance
        .GearRef(ch.qscqlmpa.wingtracker.gear.maintenance.GearKind.Wing, id)

fun aCheckId() =
    ch.qscqlmpa.wingtracker.gear.maintenance
        .CheckId("c-1")
