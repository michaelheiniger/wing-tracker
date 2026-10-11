# 6. JDK LTS through a Gradle toolchain

Date: 2026-10-11

## Status

Accepted

## Context

Developers and CI have different JDKs installed. Compose Desktop packaging and plugins track LTS releases most reliably.

## Decision

We build with an LTS JDK selected by the `jvmToolchain` setting in `build-logic`. CI and `scripts/bootstrap-ubuntu.sh` install the same major version.

## Consequences

Reproducible builds regardless of the local default JDK. Moving to the next LTS means updating the toolchain setting, CI and the script together.
