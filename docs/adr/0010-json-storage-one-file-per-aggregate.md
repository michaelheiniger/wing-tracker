# 10. JSON storage, one file per aggregate

Date: 2026-10-11

## Status

Accepted

## Context

Data starts in JSON files on a local drive; cloud or database storage comes later. Options were one file per aggregate, one file per gear type plus one for flights, or a single document.

## Decision

Each aggregate root (a piece of gear with its maintenance history, a flight) is persisted as its own JSON file in the data folder by `infrastructure-storage-json`, behind repository ports defined inward of infrastructure. Files carry a schema version so formats can be migrated. Persistence DTOs are separate from domain classes.

## Consequences

Small diffs, little corruption risk and friendly to sync tools; maps to one row or document per aggregate in later backends. Many files exist when flights accumulate, so listing needs an in-memory index. Writes must be atomic (write then rename).
