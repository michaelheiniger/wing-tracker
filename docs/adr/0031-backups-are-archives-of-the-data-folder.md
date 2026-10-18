# 31. Backups are archives of the data folder, restored into a new folder

Date: 2026-10-18

## Status

Accepted

## Context

The data folder is self-contained (ADR 0011), so copying it was the only backup so far: easy to forget, easy to do while the app writes, and with no way to tell a complete copy from a broken one. Restoring meant unpacking by hand and choosing the folder in Settings (ADR 0020). Alternatives considered: a logical export of every aggregate through the repository ports (independent of the backend, but a second serialized format to version, and quarantined records could not come along), automatic backups when the app starts or closes (needs a background step ADR 0012 avoids, and failures nobody sees), and merging a backup into the current logbook (needs conflict rules; a feature of its own).

## Decision

- **Port.** The application layer defines a `BackupArchive` port and the backup use cases in a `backup` package. The storage adapter implements it, since only it knows the folder's layout; another backend brings its own archive.
- **Content.** A backup is one zip of every file in the data folder as it is stored, the quarantine included (ADR 0015), plus a manifest: the archive format version, the application version, when it was made, and the size and SHA-256 checksum of each file. Files being written (temporary files) are left out. The archive is written next to its destination and renamed only once complete, and it may not be saved inside the data folder.
- **Streaming.** The archive is written straight to the path the pilot chose, so memory does not grow with the attachments. Choosing that path is part of the `FileSaver` platform port (ADR 0017).
- **Restore.** A backup is checked against its manifest, then unpacked into a free folder the pilot chooses, which then becomes the data folder through the change-and-restart flow of ADR 0020. The current folder is never overwritten and stays as a fallback.
- **Nudge.** When the last backup was made is kept in the settings. How long ago that was is computed on demand (ADR 0012); nothing is backed up without the pilot.

## Consequences

A backup is exact and restores bit for bit, and a broken or altered archive is refused before anything is unpacked. The archive format is the JSON storage layout, so it only restores into the JSON backend; a later backend needs a migration path. Secrets are kept by the OS (ADR 0029) and the data folder pointer lives outside the folder (ADR 0020), so neither is in a backup. A restored backup carries the settings from when it was made, including when the backup before it was made.
