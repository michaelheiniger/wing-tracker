# 17. Platform capabilities sit behind ports

Date: 2026-10-11

## Status

Accepted

## Context

Choosing and opening files works differently on each platform, and the shared UI must run on desktop now and on web and mobile later. Hard-wiring a desktop file dialog into shared code would block those targets.

## Decision

Capabilities that only a platform can provide, such as picking and opening files, are interfaces in the shared `ui` module (`FilePicker`, `FileOpener`, bundled as `PlatformServices`). Each platform module implements them and passes them in from its composition root. Shared code never references AWT or any other platform API.

## Consequences

Presenters stay testable with mocks, and a new target only needs its own implementations. Each new capability needs a port, and the platform implementations are not unit-tested and are checked by hand.
- 2026-10-11: Saving a file where the user chooses (`FileSaver`) is a platform port like picking and opening. The system tray notification is not a port of the shared UI: it is started by the desktop composition root, which asks the shared layers only for the text.

- 2026-10-11: The desktop file, folder and save dialogs are implemented with the FileKit library (dialogs module, version in the version catalog) instead of AWT and Swing dialogs, for native-looking choosers such as the desktop portal on Linux. It is confined to `app-desktop`; the ports are unchanged, so another platform can use a different implementation.
- 2026-10-12: `FilePicker` can also choose a folder and returns every track in it and its subfolders, so a logger mounted as a drive can be imported without the pilot knowing where it keeps its files. The desktop implementation walks the folder off the UI thread.
