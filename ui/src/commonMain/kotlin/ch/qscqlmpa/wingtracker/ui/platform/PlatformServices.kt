package ch.qscqlmpa.wingtracker.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment

/** Lets the user choose files; each platform has its own picker, so the shared UI only sees this port. */
interface FilePicker {
    /** Returns the chosen files with one of the given [extensions] (without the dot), or none when cancelled. */
    suspend fun pick(
        title: String,
        extensions: Set<String>,
    ): List<NewAttachment>

    /**
     * Lets the user choose one file with one of the given [extensions] (without the dot), for a file too large to
     * read at once, which the caller then reads itself. Returns its path, or null when the dialog was cancelled.
     */
    suspend fun pickPath(
        title: String,
        extensions: Set<String>,
    ): String?

    /**
     * Lets the user choose a folder, e.g. a logger mounted as a drive, and returns it with every file with the given
     * [extension] (without the dot) found in it and its subfolders, or null when the dialog was cancelled.
     * The dialog opens at [startIn] when that folder still exists.
     */
    suspend fun pickFolder(
        title: String,
        extension: String,
        startIn: String?,
    ): PickedFolder?
}

class PickedFolder(
    val path: String,
    val files: List<NewAttachment>,
)

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

    /**
     * Lets the user choose where a file will be saved, for a file too large to hold in memory, which the caller
     * then writes itself. Returns the chosen path, or null when the dialog was cancelled.
     */
    suspend fun choose(
        title: String,
        suggestedName: String,
    ): String?
}

/** Lets the user drop files onto a part of the window; only desktop has this, so it is a port. */
interface FileDrop {
    /**
     * A modifier that accepts dropped files. [onHover] tells whether files are currently held over the area;
     * [onDropped] gets the files with the given [extension] and how many dropped files were left out; [onFailed] gets
     * why the files could not be read.
     */
    @Composable
    fun target(
        extension: String,
        onHover: (Boolean) -> Unit,
        onDropped: (files: List<NewAttachment>, ignored: Int) -> Unit,
        onFailed: (Exception) -> Unit,
    ): Modifier
}

/**
 * Thrown by the ports when the system cannot name [path], e.g. a Linux system whose configured locale is not
 * installed and so cannot express accents in file names; the user can fix that, so it is not a generic failure.
 */
class UnsupportedPathException(
    val path: String,
    cause: Throwable? = null,
) : Exception("The system cannot name $path", cause)

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

/** Where the application writes its log, so a pilot can attach it to a bug report; each platform decides where. */
interface LogLocation {
    val path: String

    /** Shows the log folder in the system's file manager. */
    suspend fun open()
}

/** How handing a downloaded installer to the system went. */
sealed interface InstallOutcome {
    data object Installed : InstallOutcome

    // The system cannot replace a running application: it installs when the application closes for [restart].
    data object InstallsOnRestart : InstallOutcome

    // The pilot closed the system's password prompt.
    data object Cancelled : InstallOutcome

    data class Failed(
        val detail: String?,
    ) : InstallOutcome

    /** The system offers no way to install from the app; [command] installs it by hand, when there is one. */
    data class Manual(
        val command: String?,
    ) : InstallOutcome
}

/** Installs a verified update and starts its new version; each platform has its own installer (ADR 0024). */
interface UpdateInstaller {
    /** Hands the installer at [path] to the system, which asks for the rights it needs. */
    suspend fun install(path: String): InstallOutcome

    /** Shows the installer in the system's file manager, for installing it by hand. */
    suspend fun showInFolder(path: String)

    /** Starts the application again, so the installed version runs; installs first if that waits for the restart. */
    fun restart()
}

/**
 * Keeps secrets, such as the air-time token, in the operating system's credential store, so they never sit in a file
 * of the application (ADR 0029). Every call throws [CredentialStoreException] when the store cannot be used.
 */
interface CredentialStore {
    /** The secret kept under [key], or null when there is none. */
    suspend fun read(key: String): String?

    suspend fun write(
        key: String,
        secret: String,
    )

    /** Removing a secret that is not there is not an error. */
    suspend fun delete(key: String)
}

/** The system has no credential store, or it refused; the caller then keeps the secret for the session only. */
class CredentialStoreException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class PlatformServices(
    val filePicker: FilePicker,
    val fileOpener: FileOpener,
    val fileSaver: FileSaver,
    val fileDrop: FileDrop,
    val dataLocation: DataLocation,
    val logLocation: LogLocation,
    val updateInstaller: UpdateInstaller,
    val credentialStore: CredentialStore,
    // False when some file names cannot be opened on this system at all, which the user should hear about up front.
    val supportsAllFileNames: Boolean = true,
)
