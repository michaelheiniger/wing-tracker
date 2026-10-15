package ch.qscqlmpa.wingtracker.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.nio.file.Path

/** Shows [folder] in the system's file manager. */
internal suspend fun openInFileManager(folder: Path) =
    withContext(Dispatchers.IO) {
        val opened =
            Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.OPEN) &&
                runCatching { Desktop.getDesktop().open(folder.toFile()) }.isSuccess
        // Some Linux desktops have no AWT support for opening folders.
        if (!opened) ProcessBuilder("xdg-open", folder.toString()).start()
        Unit
    }
