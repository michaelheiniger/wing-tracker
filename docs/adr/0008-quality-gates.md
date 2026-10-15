# 8. Quality gates run by check

Date: 2026-10-11

## Status

Accepted

## Context

A long-lived codebase needs automated guards for style, design and architecture rules.

## Decision

`./gradlew check` runs the tests, Spotless with ktlint (formatting), detekt (static analysis, configured in `config/detekt`), Kover (coverage reports) and Konsist architecture tests in the `architecture-test` module. No coverage threshold is enforced yet. CI runs the same task. Tests follow the test pyramid with JUnit 5, MockK and AssertJ.

## Consequences

One command gives the same verdict locally and in CI. detekt is on a pre-release line because no stable release supports current Kotlin, so its configuration may need adjusting on upgrades; revisit when a stable release exists.

## Amendments

- 2026-10-16: `check` also runs `verifyRuntimeModules` in `app-desktop`, which compares the JDK modules the application and its libraries use (found with `jdeps`) with those in the packaged runtime. A missing module otherwise only fails in the installed app, as the Syride import did. Every module found is packaged, so the check has no exceptions to keep up to date.
