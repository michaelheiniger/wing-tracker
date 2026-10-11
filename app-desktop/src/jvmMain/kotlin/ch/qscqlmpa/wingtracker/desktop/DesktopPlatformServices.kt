package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.ui.platform.FileOpener
import ch.qscqlmpa.wingtracker.ui.platform.FilePicker
import ch.qscqlmpa.wingtracker.ui.platform.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes

/** The operating system's own file dialog, so it looks and behaves like every other desktop application. */
class DesktopFilePicker : FilePicker {
    override suspend fun pickPdfs(title: String): List<NewAttachment> {
        // AWT dialogs belong on the UI thread; the files are then read off it.
        val files =
            withContext(Dispatchers.Swing) {
                val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
                dialog.isMultipleMode = true
                dialog.setFilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
                dialog.isVisible = true
                dialog.files.toList()
            }
        return withContext(Dispatchers.IO) { files.map { NewAttachment(it.name, it.readBytes()) } }
    }
}

/** Writes the file to a private temporary folder and hands it to the default viewer. */
class DesktopFileOpener : FileOpener {
    private val directory: Path by lazy { createTempDirectory("wing-tracker-").also { it.toFile().deleteOnExit() } }

    override suspend fun open(
        fileName: String,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            "This system cannot open files from the application"
        }
        // The stored name came from the user's disk, but never trust it as a path.
        val safeName = fileName.replace(Regex("[\\\\/]"), "_")
        val file = directory.resolve(safeName)
        file.writeBytes(bytes)
        file.toFile().deleteOnExit()
        Desktop.getDesktop().open(file.toFile())
    }
}

/** The operating system's own save dialog. */
class DesktopFileSaver : FileSaver {
    override suspend fun save(
        title: String,
        suggestedName: String,
        bytes: ByteArray,
    ): String? {
        val chosen =
            withContext(Dispatchers.Swing) {
                val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
                dialog.file = suggestedName
                dialog.isVisible = true
                dialog.files.firstOrNull()
            } ?: return null
        withContext(Dispatchers.IO) { chosen.writeBytes(bytes) }
        return chosen.name
    }
}
