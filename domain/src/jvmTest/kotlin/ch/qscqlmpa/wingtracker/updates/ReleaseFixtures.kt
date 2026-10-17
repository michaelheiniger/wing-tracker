package ch.qscqlmpa.wingtracker.updates

private const val RELEASES = "https://github.com/michaelheiniger/wing-tracker/releases"

fun aDebAsset(version: String = "0.1.0") =
    ReleaseAsset("wing-tracker_${version}_amd64.deb", "$RELEASES/download/v$version/wing-tracker_${version}_amd64.deb")

fun anMsiAsset(version: String = "0.1.0") =
    ReleaseAsset("wing-tracker-$version.msi", "$RELEASES/download/v$version/wing-tracker-$version.msi")

fun aRelease(
    version: String = "0.1.0",
    assets: List<ReleaseAsset> = listOf(aDebAsset(version)),
) = Release(
    version = checkNotNull(AppVersion.parseOrNull(version)),
    notes = "### Added\n- Wing Tracker tells you when a new version is out.",
    pageUrl = "$RELEASES/tag/v$version",
    assets = assets,
)
