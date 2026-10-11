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
