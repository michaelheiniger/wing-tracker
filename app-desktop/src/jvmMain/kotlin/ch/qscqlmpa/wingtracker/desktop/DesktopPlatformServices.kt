package ch.qscqlmpa.wingtracker.desktop

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.ui.platform.FileDrop
import ch.qscqlmpa.wingtracker.ui.platform.FileOpener
import ch.qscqlmpa.wingtracker.ui.platform.FilePicker
import ch.qscqlmpa.wingtracker.ui.platform.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes

/** The operating system's own file dialog, so it looks and behaves like every other desktop application. */
class DesktopFilePicker : FilePicker {
    override suspend fun pick(
        title: String,
        extension: String,
    ): List<NewAttachment> {
        // AWT dialogs belong on the UI thread; the files are then read off it.
        val files =
            withContext(Dispatchers.Swing) {
                val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
                dialog.isMultipleMode = true
                dialog.setFilenameFilter { _, name -> name.endsWith(".$extension", ignoreCase = true) }
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

/** Accepts files dragged from the desktop's file manager onto the window. */
class DesktopFileDrop : FileDrop {
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun target(
        extension: String,
        onHover: (Boolean) -> Unit,
        onDropped: (files: List<NewAttachment>, ignored: Int) -> Unit,
    ): Modifier {
        val scope = rememberCoroutineScope()
        val target =
            remember {
                object : DragAndDropTarget {
                    override fun onEntered(event: DragAndDropEvent) = onHover(true)

                    override fun onExited(event: DragAndDropEvent) = onHover(false)

                    override fun onEnded(event: DragAndDropEvent) = onHover(false)

                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        onHover(false)
                        val dropped = event.droppedFiles()
                        val matching = dropped.filter { it.name.endsWith(".$extension", ignoreCase = true) }
                        // Reading can be slow for big tracks, so it must not block the UI thread.
                        scope.launch {
                            val attachments =
                                withContext(Dispatchers.IO) { matching.map { NewAttachment(it.name, it.readBytes()) } }
                            onDropped(attachments, dropped.size - matching.size)
                        }
                        return dropped.isNotEmpty()
                    }
                }
            }
        return Modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { it.hasFiles() },
            target = target,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.hasFiles(): Boolean = awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.droppedFiles(): List<File> =
    if (hasFiles()) {
        @Suppress("UNCHECKED_CAST")
        (awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>)
    } else {
        emptyList()
    }
