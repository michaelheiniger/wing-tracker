# 5. Base package derives from the Gradle group

Date: 2026-10-11

## Status

Accepted

## Context

Kotlin packages and published artifacts need one consistent identity.

## Decision

All code lives under the Kotlin package equal to the Gradle `group` property set in `build-logic`. Below it, packages are named by bounded context first, then by technical role where needed.

## Consequences

Renaming the identity means one property change plus a package refactor.
