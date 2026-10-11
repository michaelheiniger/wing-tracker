# 20. The data folder location is chosen by the pilot and applied on restart

Date: 2026-10-11

## Status

Accepted

## Context

The data folder (ADR 0010, ADR 0011) sits in a per-user platform location. Pilots want to see it, open it, and keep it elsewhere, for example in a synchronised or backed-up folder. The choice cannot be stored in the data folder itself, and repositories are wired once at startup in the composition root (ADR 0007).

## Decision

Settings shows the folder in use, opens it in the system file manager, and changes it. The chosen folder is remembered in a small pointer file next to the platform default folder; without it, or when the chosen folder no longer exists, the default is used so the application never quietly starts an empty logbook. Development runs have their own pointer. A new folder takes effect at the next start: the application restarts and does not rewire live. A free (empty) folder can receive the current data, which is copied, checked by size, and only then removed from the old folder; a folder that already holds data is used as it is. A folder inside the current one or containing it is refused. The capability is a port in the shared UI (ADR 0017), implemented per platform.

## Consequences

No half-switched state is possible: until restart the old folder stays authoritative, and the restart prompt cannot be dismissed. Moving is safe against a failed copy, but leftovers in the old folder can remain if removing them fails. A pointer file lives outside the data folder and is not part of a backup of it. Other platforms (mobile, web) will implement the port differently or not at all.
