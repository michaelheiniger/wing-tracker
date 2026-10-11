# 15. Unreadable stored data is quarantined, never deleted

Date: 2026-10-11

## Status

Accepted

## Context

One unreadable file must not lock the pilot out of the logbook, and silent deletion would destroy data that may be repairable by hand.

## Decision

Repositories load every record they can and report the rest as unreadable records next to the loaded ones. A corrupt record is moved to a quarantine folder inside the data folder, never deleted or overwritten, and the UI warns the user. A record written by a newer application version is intact, so it is left in place and only reported. Settings are preferences rather than user data: an unreadable settings file is quarantined and defaults apply, so the application always starts.

## Consequences

The application survives partial corruption and nothing is lost. A quarantined record is missing from the app until repaired and moved back. Other storage backends must offer the same behaviour through the repository ports.
