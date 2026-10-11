package ch.qscqlmpa.wingtracker.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment

/** Lets the user choose files; each platform has its own picker, so the shared UI only sees this port. */
interface FilePicker {
    /** Returns the chosen files with the given [extension] (without the dot), or none when the dialog was cancelled. */
    suspend fun pick(
        title: String,
        extension: String,
    ): List<NewAttachment>
}

/** Opens a file in the platform's default viewer. */
interface FileOpener {
    suspend fun open(
        fileName: String,
        bytes: ByteArray,
    )
}

/** Lets the user save a file where they choose, e.g. an exported calendar. */
interface FileSaver {
    /** Returns the name the file was saved under, or null when the dialog was cancelled. */
    suspend fun save(
        title: String,
        suggestedName: String,
        bytes: ByteArray,
    ): String?
}

/** Lets the user drop files onto a part of the window; only desktop has this, so it is a port. */
interface FileDrop {
    /**
     * A modifier that accepts dropped files. [onHover] tells whether files are currently held over the area;
     * [onDropped] gets the files with the given [extension] and how many dropped files were left out.
     */
    @Composable
    fun target(
        extension: String,
        onHover: (Boolean) -> Unit,
        onDropped: (files: List<NewAttachment>, ignored: Int) -> Unit,
    ): Modifier
}

/** What a folder chosen as the new data location turned out to be. */
enum class FolderCheck {
    // Nothing in it yet: the current data can be moved here.
    Empty,

    // Already holds files, e.g. a restored backup: it can only be used as it is.
    HasData,

    // Cannot be written to, or is not a folder.
    Unusable,

    // The folder in use now.
    Same,

    // Inside the current folder or containing it, so moving would copy a folder into itself.
    Overlaps,
}

/** Where the application keeps its data, and the ways to look at it and move it; each platform decides how. */
interface DataLocation {
    val path: String
    val defaultPath: String
    val isDefault: Boolean

    /** Lets the user choose a folder; null when the dialog was cancelled. */
    suspend fun chooseFolder(title: String): String?

    suspend fun inspect(folder: String): FolderCheck

    /** Remembers [folder] for the next start, moving the current data into it when [moveData] is set. */
    suspend fun change(
        folder: String,
        moveData: Boolean,
    )

    /** Shows the data folder in the system's file manager. */
    suspend fun open()

    /** Starts the application again so a changed location takes effect. */
    fun restart()
}

class PlatformServices(
    val filePicker: FilePicker,
    val fileOpener: FileOpener,
    val fileSaver: FileSaver,
    val fileDrop: FileDrop,
    val dataLocation: DataLocation,
)
