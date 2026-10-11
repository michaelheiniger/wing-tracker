package ch.qscqlmpa.wingtracker.ui.platform

import ch.qscqlmpa.wingtracker.gear.wing.maintenance.NewAttachment

/** Lets the user choose files; each platform has its own picker, so the shared UI only sees this port. */
interface FilePicker {
    /** Returns the chosen PDFs, or an empty list when the dialog was cancelled. */
    suspend fun pickPdfs(title: String): List<NewAttachment>
}

/** Opens a file in the platform's default viewer. */
interface FileOpener {
    suspend fun open(
        fileName: String,
        bytes: ByteArray,
    )
}

class PlatformServices(
    val filePicker: FilePicker,
    val fileOpener: FileOpener,
)
