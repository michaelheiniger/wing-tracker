# 34. Notifications show in the window, or in the desktop's notification area through a port

Date: 2026-10-24

## Status

Accepted

## Context

Some things are worth telling the pilot in passing, without a place of their own on screen and without stopping them: the first is a sync at start that found no connection (ADR 0033), which opens the logbook at once and so far said nothing. The only messages of this kind were the undo message, one at a time at the bottom of the window, and the start-up reminder, handed to the desktop's notification area (ADR 0012). Alternatives considered: the undo snackbar (one message at a time, so a notification would wait behind an undo or push it away) and the desktop's notification area only (outside the app's look, and the app cannot reliably take a message back after a while).

## Decision

- **Centre.** The shared UI has one `NotificationCenter` for the window. Anything may post an `AppNotification`: a message resource, a severity with its icon (ADR 0019), a channel and a duration. Text stays a resource until shown (ADR 0014).
- **In the window.** Notifications stack in the window's top-right corner, newest last, clear of the undo message. Each disappears after its duration, set by default in the shared UI, which pauses while the pointer is over it; a close button removes it at once. A notification without duration stays until closed.
- **System channel.** A notification may ask for the desktop's notification area, through a `SystemNotifier` platform port (ADR 0017), implemented on the desktop by the tray notifier the start-up reminder uses. When the desktop has no notification area, it shows in the window instead.
- **Lifetime.** The centre lives for the whole run, outside the window rebuilt after an unexpected error, so a notification survives the rebuild.

## Consequences

Any feature can tell something in passing with one call, in the pilot's language and in the app's look, and a later one can reach the desktop's notification area without a new mechanism. Notifications are not kept: one shown while the pilot looks away is gone after its duration. The start-up reminder still calls its notifier directly, since it must know whether the system took the message.
