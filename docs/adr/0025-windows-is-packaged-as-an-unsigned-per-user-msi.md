# 25. Windows is packaged as an unsigned per-user MSI

Date: 2026-10-17

## Status

Accepted

## Context

Pilots on Windows have no package (ADR 0009 produced Linux packages only), and the built-in updater (ADR 0024) needs one it can install. Installers must be built on their target OS. An installer that needs administrator rights would ask for them on every update. Windows locks the files of a running application, so an update cannot replace them while the app runs. Code signing was considered: Azure Artifact Signing does not issue certificates to individual developers outside a few countries, SignPath Foundation requires an OSI-approved licence for the whole project and signs under its own name, and bought certificates cost money every year. None of them avoids SmartScreen warnings for a new publisher any more, because reputation has to be earned by downloads either way. macOS is left out until someone asks for it.

## Decision

- **Package.** The release builds a per-user MSI with the Compose Gradle plugin, so installing and updating need no administrator rights. Its upgrade code is fixed in the build script of `app-desktop` and never changes, so a newer MSI replaces the installed version instead of installing next to it. It installs below the per-user programs folder, apart from the per-user folders that keep the log, the update preferences and downloads.
- **Build.** The release workflow builds the MSI on a Windows runner next to the Linux package. Both build jobs run the full `check` first, and the CI workflow runs it on Windows as well, so Windows-only bugs are caught before release. A final job computes the checksum file over all installers and publishes them together.
- **Signing.** The installer is not signed for now. Pilots confirm the SmartScreen warning on their first install from the browser. Signing is revisited once there are Windows users.
- **Updating.** After the checksum check (ADR 0024), the app starts a short PowerShell step and exits. That step waits until the app has closed, runs the MSI showing only its progress, and starts the new version.

## Consequences

Windows pilots get an installer and one-click updates without administrator rights. The first install shows an "unknown publisher" warning that pilots have to accept. Files the app downloads itself carry no mark that they came from the internet, so updates are expected not to trigger it, which remains to be confirmed on Windows. Releases take longer because two operating systems build and test, and tests must hold on both. The hand-off cannot be tested on Linux and is checked by hand on Windows.
