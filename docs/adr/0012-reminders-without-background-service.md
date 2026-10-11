# 12. Reminders without a background service

Date: 2026-10-11

## Status

Accepted

## Context

Checks are due on a scale of months, so reminders need not be real-time. A background process would need per-OS work and a larger architectural commitment.

## Decision

Due status is computed by the domain on demand from rules and flight hours. It is surfaced in an in-app dashboard badge, a desktop notification when the app starts, and an `.ics` calendar export of planned checks. There is no background service. Overdue is a flag only and never blocks logging flights.

## Consequences

Works identically on every platform we may add. A pilot who never opens the app gets no notification; the calendar export is the mitigation. Revisit by superseding this ADR if a background notifier is wanted.
- 2026-10-11: "Due soon" is decided by two lead times kept in the settings: days before a date-based limit and flight hours before a wing's hour limit. The in-app presentation is an attention panel above the logbook plus a count badge on the Gear item; the start-up notification goes through the system tray at most once per calendar day, and a day is only counted once the system accepted the message. The calendar export (iCalendar) is saved through a file dialog and contains planned checks and the date-based due dates still ahead, each with a stable id and an alarm at the lead time; limits counted in flight hours have no date and are left out.

