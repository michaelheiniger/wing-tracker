# Architecture Decision Records

ADRs capture **why** wing-tracker is built the way it is. They use the
[Nygard format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions) and stay short.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-kotlin-multiplatform-with-gradle.md) | Kotlin Multiplatform with Gradle, desktop first | Accepted |
| [0003](0003-compose-multiplatform-ui.md) | Compose Multiplatform for the shared UI | Accepted |
| [0004](0004-modules-by-layer.md) | Gradle modules by layer, bounded contexts as packages | Accepted |
| [0005](0005-base-package.md) | Base package derives from the Gradle group | Accepted |
| [0006](0006-jdk-toolchain.md) | JDK LTS through a Gradle toolchain | Accepted |
| [0007](0007-koin-dependency-injection.md) | Koin for dependency injection | Accepted |
| [0008](0008-quality-gates.md) | Quality gates run by `check` | Accepted |
| [0009](0009-release-and-versioning.md) | Releases, semantic versioning and changelog | Accepted |
| [0010](0010-json-storage-one-file-per-aggregate.md) | JSON storage, one file per aggregate | Accepted |
| [0011](0011-attachments-copied-into-data-folder.md) | Attachments are copied into the data folder | Accepted |
| [0012](0012-reminders-without-background-service.md) | Reminders without a background service | Accepted |
| [0013](0013-gear-and-maintenance-domain-model.md) | Gear and maintenance domain model | Accepted |
| [0014](0014-internationalization-with-compose-resources.md) | Internationalization with Compose resources | Accepted |
| [0015](0015-unreadable-stored-data-is-quarantined.md) | Unreadable stored data is quarantined, never deleted | Accepted |
| [0016](0016-statistics-charts-drawn-with-compose-canvas.md) | Statistics charts are drawn with Compose Canvas | Accepted |
| [0017](0017-platform-capabilities-behind-ports.md) | Platform capabilities sit behind ports | Accepted |
| [0018](0018-igc-import-through-a-port.md) | IGC import goes through a port | Accepted |
| [0019](0019-theme-and-status-colors.md) | A brand theme with checked contrast and status colours | Accepted |
| [0020](0020-data-folder-location-is-user-chosen.md) | The data folder is chosen by the pilot and applied on restart | Accepted |
| [0021](0021-syride-import-through-a-port.md) | Syride flights are imported through a port that reads public pages | Accepted |
| [0022](0022-spreadsheet-import-through-a-port.md) | Spreadsheet logbooks are imported through a port, matched by content | Accepted |
| [0023](0023-logging-to-rotating-files.md) | Logging through a facade to rotating files in the platform log folder | Accepted |
| [0024](0024-built-in-updater-notifies-and-hands-off.md) | The built-in updater checks GitHub Releases and hands the installer to the system | Accepted |
| [0025](0025-windows-is-packaged-as-an-unsigned-per-user-msi.md) | Windows is packaged as an unsigned per-user MSI | Accepted |
| [0026](0026-tracks-are-linked-to-existing-flights.md) | Tracks are linked to existing flights | Accepted |
| [0027](0027-flight-map-with-mapcompose-and-a-tile-port.md) | The flight map is drawn with MapCompose from tiles behind a port | Accepted |
| [0028](0028-ground-altitude-from-terrain-tiles.md) | The ground under a track comes from terrain tiles behind a port | Accepted |
| [0029](0029-airtime-export-through-a-port.md) | Flights are exported to air-time through its API, with a token kept by the OS | Accepted |
| [0030](0030-site-names-are-kept-per-source.md) | Site names are kept per source | Accepted |
| [0031](0031-backups-are-archives-of-the-data-folder.md) | Backups are archives of the data folder, restored into a new folder | Accepted |
| [0032](0032-backups-are-uploaded-to-s3-compatible-storage.md) | Backups are uploaded to S3-compatible storage through a port, signed by the application | Accepted |
| [0033](0033-logbook-sync-through-git.md) | The logbook is synchronised between devices through a Git repository | Accepted |

## Rules

1. **Binding, not background.** Check a change against the relevant ADRs before implementing it.
   A change that contradicts an *Accepted* ADR is not made silently: name the ADR, explain the
   trade-off it protects, then either reconsider or consciously supersede it.
2. **One decision per record**, named `NNNN-short-title.md`, numbered sequentially, never reused.
3. **Concise**: a few lines per section. Link to code or docs instead of copying them.
4. **No configuration values.** Never write the concrete value of a configuration property
   (versions, ports, paths, thresholds) in an ADR. Name the property and where it lives, so the
   record cannot silently drift from the code.
5. **Index** every record in the table above in the same change that adds it.
6. Suggest a new or amended ADR whenever a change touches an architectural decision.

## Template

```markdown
# NNNN. Title

Date: YYYY-MM-DD

## Status

Proposed | Accepted | Deprecated | Superseded by [NNNN](NNNN-title.md)

## Context

The forces at play: what problem, which constraints, which alternatives were considered.

## Decision

What we decided, in active voice ("We will ...").

## Consequences

What becomes easier, what becomes harder, what we accept.
```

## How to use these records

### Statuses

- **Proposed**: under discussion, not binding yet.
- **Accepted**: binding.
- **Deprecated**: no longer applies and nothing replaces it.
- **Superseded by NNNN**: replaced by a newer record. Never delete it; the history is the point.

### Amend or supersede?

Ask: *would someone who agreed with the old record still agree afterwards?*

- **Yes → amend in place.** The decision holds; only its extent or wording moves
  (e.g. a new module follows the same rule, a clarification, a corrected term).
  Add an `## Amendments` section at the bottom of the record (create it on first use) with one
  dated bullet per change: `- YYYY-MM-DD: what changed and why.` Edit the relevant text so the
  record reads correctly today. The status stays *Accepted*.
- **No → supersede.** They would have to change their mind. Write a new ADR that explains the
  change, set the old record's status to `Superseded by [NNNN](...)`, and update the index.
  Leave the old text untouched.

When in doubt, ask rather than guess.
