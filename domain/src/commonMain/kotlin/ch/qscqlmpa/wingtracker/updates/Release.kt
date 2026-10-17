package ch.qscqlmpa.wingtracker.updates

/** The kind of installer this installation was set up with, so an update offers the same kind. */
enum class InstallerKind(
    val fileExtension: String,
) {
    Deb("deb"),
    Msi("msi"),
}

/** A file published with a release, such as an installer. */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

/** A published release; [notes] are its changelog section, in Markdown. */
data class Release(
    val version: AppVersion,
    val notes: String,
    val pageUrl: String,
    val assets: List<ReleaseAsset>,
) {
    fun installerFor(kind: InstallerKind): ReleaseAsset? =
        assets.firstOrNull { it.name.endsWith(".${kind.fileExtension}", ignoreCase = true) }
}

/**
 * A newer release this installation can move to. [releases] are every release newer than the installed one, newest
 * first, so the pilot sees everything that changed and not only the latest notes.
 */
data class AvailableUpdate(
    val releases: List<Release>,
    val installer: ReleaseAsset,
    val skipped: Boolean,
) {
    init {
        require(releases.isNotEmpty()) { "An update needs at least one release" }
    }

    val latest: Release get() = releases.first()

    companion object {
        /**
         * The update from [installed] among [published], or null when there is none. A release without an installer
         * of [kind] is not offered, since the pilot could not install it; [skipped] is the version the pilot chose
         * not to be reminded of.
         */
        fun find(
            installed: AppVersion,
            published: List<Release>,
            kind: InstallerKind,
            skipped: AppVersion?,
        ): AvailableUpdate? {
            val newer = published.filter { it.version > installed }.sortedByDescending { it.version }
            val target = newer.firstOrNull { it.installerFor(kind) != null } ?: return null
            val shown = newer.dropWhile { it !== target }
            return AvailableUpdate(shown, checkNotNull(target.installerFor(kind)), skipped == target.version)
        }
    }
}
