# 9. Releases, semantic versioning and changelog

Date: 2026-10-11

## Status

Accepted

## Context

Users need clear versions and to know what changed. A fourth hotfix segment is not Semantic Versioning, and the packaging tool expects three numeric segments.

## Decision

Versions follow strict Semantic Versioning (MAJOR.MINOR.PATCH); a hotfix is a patch release. `CHANGELOG.md` follows Keep a Changelog and lists only user-facing changes under `Unreleased` until release. A release is a `vMAJOR.MINOR.PATCH` git tag: the release workflow derives the installer version from it, builds the native package with the Compose Gradle plugin and publishes it as a GitHub Release using that version's changelog section. The workflow fails when the section is missing. Installers must be built on the target OS, so only Linux packages are produced for now.

## Consequences

Releasing is a tag push. Every release needs a hand-written changelog entry. Other OS packages and auto-update are deferred decisions.
- 2026-10-11: The release workflow can also be started by hand as a dry run: it builds the package and uploads it as an artifact, and publishes nothing. Both workflows run on a pinned runner version, so a new default image cannot change the build unnoticed.

- 2026-10-11: A release is started by running the release workflow by hand with the version, instead of by pushing a tag. The workflow itself dates the `Unreleased` changelog section, builds and checks the package, and only then commits the changelog to `main`, creates the tag and publishes the release, so the changelog, tag and package cannot disagree. A dry run option builds without changing anything. Pushing a tag by hand no longer starts a release.
