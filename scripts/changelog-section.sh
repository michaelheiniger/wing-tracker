#!/usr/bin/env bash
# Prints the CHANGELOG.md body of one version; fails if the version has no section,
# so a release cannot go out without user-facing notes.
set -euo pipefail

version="${1:?usage: changelog-section.sh <version>}"
changelog="$(dirname "$0")/../CHANGELOG.md"

section="$(awk -v v="$version" '
  /^## \[/ { in_section = (index($0, "## [" v "]") == 1); next }
  in_section { print }
' "$changelog")"

if [[ -z "${section//[[:space:]]/}" ]]; then
  echo "No CHANGELOG.md section found for version $version" >&2
  exit 1
fi
printf '%s\n' "$section"
