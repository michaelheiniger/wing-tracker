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
