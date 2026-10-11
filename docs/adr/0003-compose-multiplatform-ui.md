# 3. Compose Multiplatform for the shared UI

Date: 2026-10-11

## Status

Accepted

## Context

Alternatives: native UI per platform with only the domain shared (best native feel, multiplied UI work), or one shared UI.

## Decision

We use Compose Multiplatform with Material 3 for one declarative UI codebase across desktop, and later Android, iOS and web. A light/dark theme exists from the start; the user's choice is held by `ThemePreference`.

## Consequences

Less UI code to maintain. The iOS and web targets are less mature than desktop and Android, and the desktop look is less native. Platform-specific needs go behind interfaces.
- 2026-10-11: The Material 3 theme is customised with the application's own colours, shapes and type weights (see [ADR 0019](0019-theme-and-status-colors.md)).
- 2026-10-11: The interface size follows the desktop's scaling by default and can be set in the settings. The desktop module applies it on top of the density the JVM chose, and only to the extent the JVM has not scaled itself, because on Linux under Wayland the JVM draws at one pixel per unit and ignores the desktop's scale.

