# 30. Site names are kept per source

Date: 2026-10-18

## Status

Accepted

## Context

A flight's take-off and landing sites were one name each, with no record of where it came from. Names come from several places: the pilot types them, the Syride import gives the take-off site, a spreadsheet gives both, and a lookup from the track's position is planned. Overwriting one with another loses information, and the pilot cannot tell an imported name from one typed by hand. The take-off and landing positions are known from a track, but ADR 0018 stores nothing derived from it.

## Decision

Each site keeps one name per source (`SiteNames` in the domain, sources in `SiteSource`), not only the current one. The sources are ordered by precedence, the pilot's own name first, then Syride, then a spreadsheet: the first one with a name is the one the logbook shows, exports and matches spreadsheet rows against. A search looks in every source's name. Editing a flight only changes the pilot's own name; clearing it shows the imported name again, and imported names cannot be deleted from the form. Importing again from Syride leaves the stored Syride names as they are.

The positions of take-off and landing are not stored: they stay in the track and the flight's page works them out each time it reads it, as ADR 0018 says for everything derived from a track.

The stored flight format gains the names per source in a new schema version (version in `FlightDocument`). A name stored before has no source; it is read as Syride's for the take-off site of a flight imported from Syride, and as the pilot's otherwise.

## Consequences

No source overwrites another, and a later name lookup becomes one more source that never replaces what the pilot typed. The inferred source of older names can be wrong for a Syride name the pilot edited before, or a name that came from a spreadsheet, which left no trace. Showing the positions from the track only works where the track is read, so the logbook and exports know only names. Older application versions treat flights written in the new format as newer data and leave them untouched (ADR 0015).
