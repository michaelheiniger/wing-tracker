package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.ui.platform.DataLocation
import ch.qscqlmpa.wingtracker.ui.platform.FolderCheck
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.system.exitProcess

/**
 * The data folder on a desktop: [current] is in use now, [default] is the platform's choice, and the folder the pilot
 * picks is remembered in [pointerFile] (see [DataDirectory]) and used from the next start.
 */
class DesktopDataLocation(
    private val current: Path,
    private val default: Path,
    private val pointerFile: Path,
) : DataLocation {
    override val path: String get() = current.toString()
    override val defaultPath: String get() = default.toString()
    override val isDefault: Boolean get() = current.normalize() == default.normalize()

    override suspend fun chooseFolder(title: String): String? =
        FileKit
            .openDirectoryPicker(
                directory = PlatformFile(current.parent?.toFile() ?: current.toFile()),
                dialogSettings = FileKitDialogSettings(title = title),
            )?.path

    override suspend fun inspect(folder: String): FolderCheck =
        withContext(Dispatchers.IO) {
            val target = Path(folder).toAbsolutePath().normalize()
            val now = current.toAbsolutePath().normalize()
            when {
                target == now -> FolderCheck.Same
                target.startsWith(now) || now.startsWith(target) -> FolderCheck.Overlaps
                !isUsable(target) -> FolderCheck.Unusable
                isEmpty(target) -> FolderCheck.Empty
                else -> FolderCheck.HasData
            }
        }

    override suspend fun change(
        folder: String,
        moveData: Boolean,
    ) = withContext(Dispatchers.IO) {
        val target = Path(folder).toAbsolutePath().normalize()
        val check = inspect(folder)
        check(check == FolderCheck.Empty || (check == FolderCheck.HasData && !moveData)) {
            "The folder cannot be used: $check"
        }
        target.createDirectories()
        if (moveData) copyAll(current, target)
        // The pointer only changes once the copy is complete, so a failure leaves the old folder in charge.
        remember(target)
        // Everything is safe in the new place; leftovers in the old one are harmless, so failing here is no failure.
        if (moveData) runCatching { removeContentsOf(current) }
    }

    override suspend fun open() =
        withContext(Dispatchers.IO) {
            val opened =
                Desktop.isDesktopSupported() &&
                    Desktop.getDesktop().isSupported(Desktop.Action.OPEN) &&
                    runCatching { Desktop.getDesktop().open(current.toFile()) }.isSuccess
            // Some Linux desktops have no AWT support for opening folders.
            if (!opened) ProcessBuilder("xdg-open", current.toString()).start()
            Unit
        }

    override fun restart() {
        // Started before exiting: a failed start must still leave the pilot with the application closed, not half-open.
        val info = ProcessHandle.current().info()
        info.command().ifPresent { command ->
            runCatching { ProcessBuilder(listOf(command) + info.arguments().orElse(emptyArray())).inheritIO().start() }
        }
        exitProcess(0)
    }

    private fun remember(target: Path) {
        if (target == default.toAbsolutePath().normalize()) {
            pointerFile.deleteIfExists()
            return
        }
        pointerFile.parent?.createDirectories()
        val temporary = pointerFile.resolveSibling("${pointerFile.fileName}.tmp")
        temporary.writeText(target.toString())
        Files.move(temporary, pointerFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun isUsable(target: Path): Boolean {
        if (target.exists()) return target.isDirectory() && Files.isWritable(target)
        // A folder that does not exist yet is created, which needs a writable folder above it.
        val parent = generateSequence(target.parent) { it.parent }.firstOrNull { it.exists() }
        return parent != null && parent.isDirectory() && Files.isWritable(parent)
    }

    private fun isEmpty(target: Path): Boolean = !target.exists() || target.listDirectoryEntries().isEmpty()

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private fun copyAll(
        from: Path,
        to: Path,
    ) {
        val files = from.walk().filter { it.isRegularFile() }.toList()
        files.forEach { file ->
            val destination = to.resolve(file.relativeTo(from).toString())
            destination.parent.createDirectories()
            file.copyTo(destination)
            // A copy that is not byte-for-byte the same size must never be followed by deleting the original.
            if (destination.fileSize() != file.fileSize()) throw IOException("Copying ${file.fileName} was incomplete")
        }
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private fun removeContentsOf(folder: Path) = folder.listDirectoryEntries().forEach { it.deleteRecursively() }
}

internal fun dataLocation(
    dataDirectory: Path,
    devMode: Boolean,
) = DesktopDataLocation(dataDirectory, DataDirectory.resolve(devMode), DataDirectory.pointerFile(devMode))
