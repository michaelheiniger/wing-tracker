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
