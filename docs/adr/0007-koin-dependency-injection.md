# 7. Koin for dependency injection

Date: 2026-10-11

## Status

Accepted

## Context

Options were manual constructor injection (no framework), Koin (runtime DSL) and kotlin-inject (compile-time, needs KSP).

## Decision

We use Koin, declared only in the composition root (`app-desktop`). Domain and application classes take dependencies through plain constructors and never reference Koin.

## Consequences

Less wiring code. Missing bindings surface at runtime, so the composition root needs a startup test once real modules exist. Replacing Koin stays cheap because no inner layer knows it.
