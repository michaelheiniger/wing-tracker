#!/usr/bin/env bash
# Prepares an Ubuntu machine for wing-tracker development. Safe to re-run.
set -euo pipefail

# Keep in sync with jvmToolchain in build-logic (ADR 0006).
JDK_PACKAGE="openjdk-21-jdk"

log() { printf '\n==> %s\n' "$*"; }

if [[ "${EUID}" -eq 0 ]]; then
  SUDO=""
else
  command -v sudo >/dev/null || { echo "sudo is required when not running as root" >&2; exit 1; }
  SUDO="sudo"
fi

log "Installing system packages"
$SUDO apt-get update
# fakeroot and binutils are needed by jpackage to build .deb installers.
$SUDO apt-get install -y --no-install-recommends \
  "${JDK_PACKAGE}" git curl unzip fakeroot binutils

log "Verifying toolchain"
java -version
git --version

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
log "Downloading Gradle distribution and running the full check"
# The wrapper pins the Gradle version, so no global Gradle install is needed.
(cd "${repo_root}" && ./gradlew check)

log "Done. Open the project in IntelliJ IDEA and use the 'wing-tracker (dev)' run configuration."
