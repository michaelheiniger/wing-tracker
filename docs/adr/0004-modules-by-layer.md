# 4. Gradle modules by layer, bounded contexts as packages

Date: 2026-10-11

## Status

Accepted

## Context

We apply DDD. Options were modules per layer, modules per bounded context, or a single module. Per-context modules were judged premature, a single module lets layer rules rot.

## Decision

Modules are `domain`, `application`, `infrastructure-*`, `ui` and `app-desktop` (composition root). Dependencies point inwards: domain depends on nothing, application only on domain, infrastructure and ui on application, and only `app-desktop` sees everything. Bounded contexts (e.g. gear, flights, maintenance) are packages inside each module. The `architecture-test` module verifies these rules with Konsist (see [ADR 0008](0008-quality-gates.md)). Storage backends implement repository ports defined in the application or domain layer and can be swapped without touching them.

## Consequences

The compiler enforces layering. Splitting a context into its own modules later remains possible. Cross-cutting changes touch several modules.
