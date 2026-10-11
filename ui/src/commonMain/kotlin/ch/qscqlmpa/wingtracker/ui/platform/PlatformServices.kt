package ch.qscqlmpa.wingtracker.ui.platform

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

class PlatformServices(
    val filePicker: FilePicker,
    val fileOpener: FileOpener,
    val fileSaver: FileSaver,
)
