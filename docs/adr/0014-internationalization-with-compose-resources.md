# 14. Internationalization with Compose resources

Date: 2026-10-11

## Status

Accepted

## Context

The application is used in several languages, and strings scattered through code are costly to translate later.

## Decision

All user-visible text lives in Compose Multiplatform string resources under `ui/src/commonMain/composeResources`, one `values` folder per locale, with the unqualified folder as the fallback. Code never contains user-visible literals. Logic that can fail reports kinds (enums or typed failures), and only the composable layer turns them into text. Dates are shown day-month-year regardless of locale, and chosen with a date picker rather than typed.

## Consequences

Adding a language means adding a resource folder only. Translations need review by a native speaker. Plain-Kotlin logic cannot produce text, so presenters and validators stay locale-free and easy to test.
