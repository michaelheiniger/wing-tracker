# 13. Gear and maintenance domain model

Date: 2026-10-11

## Status

Accepted

## Context

The product tracks wings, harnesses and reserve parachutes with maintenance, and flights per wing, for a single pilot. Manufacturers define check intervals per model by months and/or flight hours, whichever comes first, and name their services differently.

## Decision

Wing, Harness and Reserve are separate aggregates in a gear context; each has a lifecycle status (active or retired, with date and reason) and keeps its maintenance history. A wing has an in-service date and hours flown before tracking. Maintenance check types are fixed per gear type: wing partial (trim check) or full, reserve repack, harness check, and for all an 'other' type with an optional comment. Each item carries its own interval rule (months, flight hours, or both). A Flight is its own aggregate in a flights context and references exactly one wing by id; harnesses and reserves are independent of flights. Hours per wing are derived from flights plus pre-tracking hours, never stored. The app serves a single pilot, so there is no pilot concept.

## Consequences

Rules match how manufacturers specify checks and need no built-in model catalogue. Retired gear keeps statistics correct. Adding pilots, or harness and reserve usage per flight, later would extend the model and likely warrants a new ADR.

## Amendments

- 2026-10-11: A flight records only a date and a duration plus optional sites and notes, and is ordered within a day by when it was logged. It cannot be dated in the future or before its wing entered service, and its duration must be positive. Wings may be deleted only while they have no flights; otherwise they are retired.
- 2026-10-11: Maintenance lives inside the Wing aggregate. A wing has at most one interval rule (months, hours, or both, whichever comes first) that governs any periodic check. Partial and full checks restart the interval clock; an "other" check does so only when the pilot marks it. The clock starts at the in-service date when no check exists. Hours count tracked flights from the day the clock started, including flights on that day, without the hours flown before tracking. A planned check is separate from history and is completed into a performed check in one save. A wing with maintenance history can only be retired, like one with flights.
- 2026-10-11: Harness and Reserve are aggregates of their own, next to Wing. Maintenance is shared: a `MaintenanceHistory` (checks and planned checks) is embedded in each, behind one `MaintainedGear` interface, so the maintenance use cases and screens are written once. One check-type set exists and each gear kind accepts only its own: wings partial, full and other; harnesses check and other; reserves repack and other. Only wings are linked to flights, so only a wing can have an interval in flight hours; harnesses and reserves are due by months. A reserve also records its manufacture date for reference. Like wings, harnesses and reserves with maintenance history can only be retired.

