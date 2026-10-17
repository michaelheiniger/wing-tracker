# 26. Tracks are linked to existing flights

Date: 2026-10-17

## Status

Accepted

## Context

Syride knows more about a flight than its track does (site, distance, top altitude, instrument, wing), but the Syride import downloads no tracks (ADR 0021). Pilots therefore import from Syride first and then want to add the tracks. Until now every IGC import created a new flight, so the same flight ended up in the logbook twice.

A track can only be tied to a Syride flight by what both sides know:

- A track downloaded from the Syride website is named after the Syride flight id.
- A Syride logger names its tracks after the date and time of the first fix in UTC and stores no Syride id in them. Syride shows the same moment as the flight's start, in local time, to the minute.
- Date and duration alone are ambiguous: a pilot often makes several short flights of similar length on one day, and loggers also keep recordings that are not flights.

Working out the time zone from the track's position would be more exact abroad, but needs a time-zone database as a new dependency.

## Decision

A track is either attached to an existing flight or creates a new one, and the pilot confirms the choice for every track in the import review. An application-level `TrackMatcher` proposes the choice, trying in order:

1. **Syride id**: a file named after a Syride flight id matches the flight imported with that id. Syride entries without a track have ids of a different kind and are never matched.
2. **Take-off time**: a track whose first fix, converted to the computer's time zone, has the same date and minute as a flight's take-off time.
3. **Date and duration**: a flight on the same date whose duration is within a tolerance (a named constant next to the matcher). This is only a possible match and is never selected by default.

Tracks with no match are offered as new flights. On a day that already has flights they start unticked, since they are then often recordings that were not flights; on other days they start ticked, so a pilot who never imported from Syride sees no change.

To make the second rule possible, flights store an optional take-off time. The Syride import fills it, also on flights it imported before once the pilot agreed, and the IGC import fills it from the detected take-off. Within a day, flights are ordered by it, and flights without one come after those with one.

Attaching is a behaviour of the `Flight` aggregate: a flight has at most one track. An import never replaces a track that is already stored; a track matching such a flight can only create a new flight or be skipped. Attaching keeps the flight's details as they are.

## Consequences

Importing from Syride and then from the logger gives one flight per real flight, with the richer Syride details and the original track. The flight format gains an optional field and a new schema version that older application versions treat as newer data. Flights recorded in another time zone than the computer's only get a possible match, and the pilot links them by hand. The matching rules depend on how Syride names files and reports start times; if that changes, exact matches stop and only possible matches remain, which the review makes visible rather than linking wrongly. Importing IGC files first and Syride afterwards still duplicates flights; linking in that direction is left for later.

## Amendments

- 2026-10-17: A flight created from a track stores the detected take-off, not the first fix, so the field means what its name says; a logger is often switched on well before take-off. Matching is unaffected, since it compares a track's first fix with Syride's start time. Flights without a take-off time are listed after the timed ones of their day.
- 2026-10-17: On flights imported before, Syride's start time replaces the stored take-off time, also one entered by hand, but only after the pilot saw how many flights change and agreed.
- 2026-10-17: Tracks with no match start unticked only on days that already have flights. Unticking them everywhere would have unticked every track of a pilot who does not use Syride.
- 2026-10-17: The Syride import goes on to adding the tracks when Syride flights in the logbook have a track on Syride but none stored, told apart by the kind of their Syride id. Offering it right after the flights, or right after reading the account when it has no new flights, is what makes the recommended order the easy one.
- 2026-10-17: The Syride id rule also requires the flight's date to be the local date of the track's first fix. A logger's file name is a time of day made of digits only, which can equal the number of an old Syride flight, since Syride numbers flights across all its users. A track read from a device keeps the folder it was in before its name, so a logger that keeps the date in the folder name keeps it in the stored name too; the rule reads only the part after the folder.
