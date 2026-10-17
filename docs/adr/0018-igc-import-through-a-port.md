# 18. IGC import goes through a port

Date: 2026-10-11

## Status

Accepted

## Context

Flights can be imported from IGC track files. Reading the format is a solved problem, and the only IGC library found is a small JVM library, published only through JitPack, by a single maintainer. The application must stay portable to web and mobile.

## Decision

The application layer defines a `TrackReader` port and does everything else itself: it works out take-off and landing, checks for duplicates by checksum, and applies the usual flight rules. The port is implemented in its own JVM-only module, `infrastructure-igc`, with igc4j at a pinned version. The JitPack repository is restricted to igc4j's group, so nothing else can ever be resolved from it. Take-off and landing are detected from ground speed: the flight is the first stretch of sustained movement, and standing still for a while ends it. The thresholds are named constants in `FlightDetector` and the pilot can correct the result before saving. The original file is stored unmodified with its checksum (ADR 0011), and importing an identical file again asks for confirmation.

## Consequences

Replacing the library, or writing our own reader for other targets, only touches `infrastructure-igc`. A dependency on a very small project is accepted, with the third-party repository confined to it. Speed-based detection can misjudge unusual recordings, for example a logger left running in a car straight after landing, which the editable duration and the shown take-off and landing times make visible.

## Amendments

- 2026-10-11: Several tracks are imported at once, from the file chooser or by dropping files on the logbook, through one review list (ticked tracks, wing for all tracks with a per-track override). The review shows what the track says but no longer edits the date or duration, so a track without a detectable flight cannot be imported; such a flight is logged by hand. Tracks already imported, or repeated in the same selection, are unticked rather than asked about one by one. Detection, the checksum check and the flight rules are unchanged.
- 2026-10-12: Tracks can also come from a folder, such as a logger mounted as a drive, and go through the same review list. Tracks already imported are left out of that list and only counted, since a logger keeps every track ever recorded. Detection, the checksum check and the flight rules are unchanged. The folder is remembered in the settings (schema version 5).
- 2026-10-17: A track can now be attached to an existing flight instead of creating a new one, as proposed by the matcher in [ADR 0026](0026-tracks-are-linked-to-existing-flights.md) and confirmed by the pilot. The checksum check, detection and flight rules are unchanged.
