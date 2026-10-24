package ch.qscqlmpa.wingtracker.git

import ch.qscqlmpa.wingtracker.sync.RecordKind
import ch.qscqlmpa.wingtracker.sync.SyncAuthor
import ch.qscqlmpa.wingtracker.sync.SyncRemote
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevCommit
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
    private val git =
        Git
            .cloneRepository()
            .setURI(remote.url)
            .setDirectory(directory.toFile())
            .setBranch("main")
            .call()

    fun commitAndPush(
        path: String,
        content: String,
        message: String = "Log flight from the other device",
    ) {
        directory.store(path, content)
        git.add().addFilepattern(".").call()
        git
            .commit()
            .setMessage(message)
            .setAuthor(OTHER_DEVICE.name, OTHER_DEVICE.email)
            .call()
        git.push().call()
    }
}

/** What the remote holds, read as a fresh clone would see it. */
class RemoteContent(
    remote: SyncRemote,
    directory: Path,
) {
    private val git =
        Git
            .cloneRepository()
            .setURI(remote.url)
            .setDirectory(directory.toFile())
            .setBranch("main")
            .call()

    fun files(): List<String> = git.repository.filesAt(Constants.HEAD)

    fun read(path: String): String = File(git.repository.workTree, path).readText()

    fun commits(): List<RevCommit> = git.log().call().toList()
}

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
