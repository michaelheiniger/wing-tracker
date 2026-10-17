# 24. The built-in updater checks GitHub Releases and hands the installer to the system

Date: 2026-10-17

## Status

Accepted

## Context

Pilots install the app from a GitHub Release (ADR 0009) and otherwise never learn that a newer version exists. ADR 0009 deferred auto-update. Linux comes first, then Windows; macOS only if requested. The Linux package installs into a root-owned location, so the running app cannot replace itself, and ADR 0012 rules out a background service. Alternatives considered: notifying only (pilots still have to find and install the package themselves), installing silently with elevated rights (the app would run privileged commands and needs signing first), and switching to a distribution channel with its own updates such as an APT repository, Flatpak, AppImage or Conveyor (a much larger change to the release process).

## Decision

- **Check.** At startup, and from "Check now" in the update section of the settings, the app asks GitHub Releases for the published releases. Drafts and pre-releases are ignored, and so are tags that are not a version. Development runs never check. The check can be switched off. That switch belongs to the installation, not the logbook. It is stored in the platform's per-user configuration folder (the local, not the roaming, profile on Windows) and not in the data folder, which can be synchronised. A failed check (offline, rate-limited, unexpected response) is only logged.
- **Model.** Versions are compared as Semantic Versions (ADR 0009) by a value object in the domain's `updates` context. A `CheckForUpdate` use case in the application layer depends on a `ReleaseSource` port and picks the release asset for the installer kind the composition root reports. Failures are typed, so only composables turn them into text (ADR 0014).
- **Adapter.** The port is implemented in a new JVM-only module, `infrastructure-github-releases`, with the JDK HTTP client. The repository it reads is configured in the composition root.
- **Integrity.** The release workflow publishes a SHA-256 checksum file next to the installers. The adapter downloads the installer into the platform cache folder and refuses it unless its checksum matches.
- **Show.** An available update appears as a dismissible banner in the main window, with the changelog sections of every release newer than the installed one (newest first, rendered as Markdown), "Install", "Later" (for this session) and "Skip this version". A skipped version is remembered per installation, and the next version is announced as usual. Until it is installed or skipped, a badge with an icon and text (ADR 0019) on Settings and in About keeps it findable.
- **Install.** Only after the pilot clicks "Install", a platform port in `ui` (ADR 0017) hands the verified file to the system installer. On Linux that is the package manager run through polkit (`pkexec`), so the desktop asks for the administrator password, dependencies are resolved, and the app knows when installation has finished. Without those tools, the app shows the file in its folder together with the command to install it. After a successful install the app offers to restart and restarts the same way a data-folder change does (ADR 0020).

## Consequences

Pilots learn about updates and install them in a few clicks, without new infrastructure. Each start makes one request to GitHub unless switched off. The checksum detects corrupted or truncated downloads, not a compromised release, because it is published in the same place as the installer. Signing is deferred. The data stays safe across updates and downgrades thanks to ADR 0015. Windows needs its own packaging, a per-user MSI with a fixed upgrade code, and a code-signing decision, both recorded when that slice is done. Its installer hand-off is a second implementation of the same port.

## Amendments

- 2026-10-17: The app reads the list of releases instead of only the latest one, so "What's new" covers every version since the installed one. "Check now" sits in a dedicated update section of the settings, and About shows the badge. The per-installation preferences live in the platform configuration folder instead of next to the data-folder pointer: that is the conventional place, and an unreadable file can be quarantined there (ADR 0015). Release notes are rendered with the multiplatform Markdown renderer (version in the version catalog), which is confined to `ui`. Until installing from the app is built, "Download" opens the release page.
