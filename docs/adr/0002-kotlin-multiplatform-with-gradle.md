# 2. Kotlin Multiplatform with Gradle, desktop first

Date: 2026-10-11

## Status

Accepted

## Context

The product starts as a desktop app but must be able to reach web and native mobile without a rewrite of its core.

## Decision

We use Kotlin Multiplatform built with Gradle (Kotlin DSL, version catalog in `gradle/libs.versions.toml`). Only the JVM desktop target exists for now. Shared build logic lives in convention plugins under `build-logic`, so adding a target is a change in one place. All versions live only in the version catalog.

## Consequences

JUnit 5, MockK and AssertJ are JVM-only, so tests live in `jvmTest` until another target needs its own. Code in `commonMain` must stay platform-neutral, which is verified only for the targets that exist.
