package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.ui.platform.LogLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.createDirectories

/** The folder the log is written to on a desktop (see [DesktopLogging]). */
class DesktopLogLocation(
    private val directory: Path,
) : LogLocation {
    override val path: String get() = directory.toString()

    override suspend fun open() {
        // The backend creates the folder with its first line; a pilot must still find it if that failed.
        withContext(Dispatchers.IO) { directory.createDirectories() }
        openInFileManager(directory)
    }
}
