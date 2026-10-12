# 21. Syride flights are imported through a port that reads public pages

Date: 2026-10-12

## Status

Accepted

## Context

Many pilots already log their flights on Syride. Importing them saves retyping years of flights. Syride offers no official API, and the pages of a flight (and so its IGC track) are protected by a bot check, which must not be circumvented.

## Decision

The application layer defines a `SyrideFlightSource` port and does everything else itself: it works out which flights are new, groups them by the wing used on Syride, and imports them under the usual flight rules. The port is implemented in its own JVM-only module, `infrastructure-syride`, which reads the public flight list a profile page loads while scrolling. It downloads no tracks. The parsing is tested on pages saved from Syride, and when most flight cards cannot be read the import stops with a clear "Syride's pages changed" message instead of importing wrong data. Imported flights remember their Syride id (`ExternalReference`), so importing again never duplicates, and they carry the optional distance, top altitude and instrument that Syride knows. Wings that do not exist yet can be created during the import, from the wing's name and brand on Syride; each is created without an in-service date, which Syride does not know, and can be completed later. The pilot's Syride account name is remembered in the settings.

## Consequences

The integration is fragile by nature: a change in Syride's pages breaks it until the reader is updated, which is why it is isolated behind a port and covered by tests on saved pages. Tracks of Syride flights are not imported; they can be attached later with the IGC import. The stored flight format gains optional fields (schema version 3) that older application versions treat as newer data and leave untouched.
