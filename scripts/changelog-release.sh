#!/usr/bin/env bash
# Turns the Unreleased section of CHANGELOG.md into a dated release section and leaves a fresh, empty
# Unreleased one on top. Fails when there is nothing to release or the version already has a section.
set -euo pipefail

version="${1:?usage: changelog-release.sh <version> [date]}"
date="${2:-$(date +%F)}"
changelog="$(dirname "$0")/../CHANGELOG.md"

if grep -qF "## [$version]" "$changelog"; then
  echo "CHANGELOG.md already has a section for $version" >&2
  exit 1
fi

unreleased="$(awk '
  /^## \[Unreleased\]/ { in_section = 1; next }
  /^## \[/ { in_section = 0 }
  in_section { print }
' "$changelog")"
if [[ -z "${unreleased//[[:space:]]/}" ]]; then
  echo "The Unreleased section of CHANGELOG.md is empty: nothing to release" >&2
  exit 1
fi

# Written to a temporary file first so a failure never leaves a half-edited changelog.
temporary="$(mktemp)"
awk -v v="$version" -v d="$date" '
  /^## \[Unreleased\]/ && !done { print; print ""; print "## [" v "] - " d; done = 1; next }
  { print }
' "$changelog" > "$temporary"
mv "$temporary" "$changelog"
