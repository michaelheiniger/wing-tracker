# 21. Syride flights are imported through a port that reads public pages

Date: 2026-10-12

## Status

Accepted

## Context

Many pilots already log their flights on Syride. Importing them saves retyping years of flights. Syride offers no official API, and the pages of a flight (and so its IGC track) are protected by a bot check, which must not be circumvented.

## Decision

The application layer defines a `SyrideFlightSource` port and does everything else itself: it works out which flights are new, groups them by the wing used on Syride, and imports them under the usual flight rules. The port is implemented in its own JVM-only module, `infrastructure-syride`, which reads the public flight list a profile page loads while scrolling. It downloads no tracks. The parsing is tested on pages saved from Syride, and when most flight cards cannot be read the import stops with a clear "Syride's pages changed" message instead of importing wrong data. Imported flights remember their Syride id (`ExternalReference`), so importing again never duplicates, and they carry the optional take-off time, distance, top altitude and instrument that Syride knows. Wings that do not exist yet can be created during the import, from the wing's name and brand on Syride; each is created without an in-service date, which Syride does not know, and can be completed later. The pilot's Syride account name is remembered in the settings.

## Consequences

The integration is fragile by nature: a change in Syride's pages breaks it until the reader is updated, which is why it is isolated behind a port and covered by tests on saved pages. Tracks of Syride flights are not imported; they are attached afterwards through the IGC import (ADR 0026). The stored flight format gains optional fields (schema version 3) that older application versions treat as newer data and leave untouched.

## Amendments

- 2026-10-16: The pages are read with the jsoup HTML parser (version in the version catalog) instead of regular expressions, so markup details such as quoting or entities no longer matter and only the selectors describe the page. It stays confined to `infrastructure-syride`.
- 2026-10-16: The regular-expression reader cut names and sites at the first parenthesis and left most accents as HTML entities. The adapter still computes what it read for each flight (`EarlierReading`), so the application can offer to correct imported flights whose text is still exactly that, and leave texts the pilot changed alone. The pilot confirms before anything is saved. That legacy reading can be removed once such flights no longer matter.
- 2026-10-17: The legacy reading and the correction of flights it garbled are removed because they are no longer needed; the adapter only reads pages with jsoup again.
- 2026-10-17: Imported flights also keep Syride's start time as their take-off time, and importing again fills it on flights imported before, so tracks can be linked to them ([ADR 0026](0026-tracks-are-linked-to-existing-flights.md)).
- 2026-10-17: The stored id of a Syride flight without a track uses the app's own words (`untracked-flight:` followed by Syride's activity number) instead of Syride's term "activity", so stored data does not depend on Syride's vocabulary. Flights stored with the old prefix are renamed by an application use case run at each start before anything reads the data; it finds nothing once done. The file format is unchanged, so no schema version is added; a version up to 0.2.0 started on renamed data would import these flights from Syride again.
- 2026-10-17: The renaming of stored ids is removed again: the only data stored with the old prefix has been migrated, and no one else has used the app. Flights stored with `activity:` by a version up to 0.2.0 would now be imported from Syride again.
- 2026-10-17: The port also gives the address of a flight's page on Syride, built the way Syride's own pages build it, so the address format stays in the adapter. Only flights with a track have such a page. Flights do not store the account they came from, so the account saved for the import is used; storing it per flight was considered and left out, as a pilot imports from one account.
