package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.walk

/**
 * Every track below [root], however the device arranges its folders. Must run off the UI thread: a logger can hold
 * years of tracks.
 */
@OptIn(ExperimentalPathApi::class)
internal fun readTracks(
    root: Path,
    extension: String,
): List<NewAttachment> =
    // NIO keeps the names' raw bytes, so tracks are still read when the system cannot decode their names.
    root
        .walk()
        .filter { it.isRegularFile() && it.extension.equals(extension, ignoreCase = true) && !it.isHiddenBelow(root) }
        .sortedBy { it.toString() }
        .map { NewAttachment(it.nameBelow(root), it.readBytes()) }
        .toList()

// Hidden folders hold things like the trash of a USB stick, whose deleted tracks must not come back. Windows only
// honours the hidden attribute, so the leading dot that Linux and macOS use for their trash is checked too.
private fun Path.isHiddenBelow(root: Path): Boolean =
    generateSequence(this) { it.parent }.takeWhile { it != root }.any { it.name.startsWith('.') || Files.isHidden(it) }

// A logger may name a track after its time only and keep the date in the folder, so the folder stays in front.
private fun Path.nameBelow(root: Path): String = if (parent == root) name else "${parent.name}/$name"
