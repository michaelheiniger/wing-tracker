# wing-tracker

Track your paragliding gear (wings, harnesses, reserve parachutes), their maintenance history and upcoming checks, and your flights per wing. Kotlin Multiplatform with Compose; desktop first.

Architecture decisions are in [`docs/adr/`](docs/adr/README.md). User-facing changes are in [`CHANGELOG.md`](CHANGELOG.md). Working agreements for AI assistants are in [`CLAUDE.md`](CLAUDE.md).

## Development setup

### Ubuntu
```bash
scripts/bootstrap-ubuntu.sh
```
It installs the JDK and packaging prerequisites, then runs the full check once. It is safe to re-run.

### Other systems
Install the JDK major version named by `jvmToolchain` in `build-logic` and git. Everything else, including Gradle, comes from the wrapper (`./gradlew`).

### IDE
Open the project in IntelliJ IDEA. The shared run configuration **wing-tracker (dev)** starts the desktop app in development mode.

## Run the application
```bash
./gradlew :app-desktop:run -Pwingtracker.dev=true
```

## Tests and checks
```bash
./gradlew check            # tests, ktlint (Spotless), detekt, Kover, architecture tests
./gradlew spotlessApply    # auto-fix formatting
./gradlew koverHtmlReport  # merged coverage report in build/reports/kover
```

### A single test class
```bash
./gradlew :ui:jvmTest --tests 'ch.qscqlmpa.wingtracker.ui.theme.ThemeModeTest'
```

### A single test case
Nested classes use `$` and backtick names are matched as written:
```bash
./gradlew :ui:jvmTest --tests 'ch.qscqlmpa.wingtracker.ui.theme.ThemeModeTest$IsDark.Dark stays dark even when the operating system is light'
```
Replace `:ui` with the module that owns the test (`:domain`, `:application`, ...).

## Release
Versions follow Semantic Versioning (`MAJOR.MINOR.PATCH`); a hotfix is a patch release.

1. In `CHANGELOG.md`, rename `Unreleased` to the new version and date it (format: `## [x.y.z] - YYYY-MM-DD`), then add a fresh empty `Unreleased` section.
2. Make sure `./gradlew check` passes on `main`.
3. Tag and push:
   ```bash
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```
4. The `Release` GitHub workflow builds the Linux package and publishes a GitHub Release with that version's changelog section. It fails if the section is missing.

To rehearse a release without tagging, run the **Release** workflow by hand from the Actions tab (or `gh workflow run release.yml -f version=0.0.1`). It builds the package and uploads it as a workflow artifact, and publishes nothing.

To build a package locally: `./gradlew :app-desktop:packageDeb -PappVersion=X.Y.Z`; the result is under `app-desktop/build/compose/binaries`.

## Application icon
The icon is drawn by `design/generate-icon.py` (needs Python and Pillow) and written to `app-desktop/icons` (installer) and `app-desktop/src/jvmMain/resources` (window and notification). Change the geometry in the script and run `python3 design/generate-icon.py` to regenerate every size; do not edit the images by hand.

