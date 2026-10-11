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

## Sample data for development
```bash
./gradlew seedDevData
```
Deletes the development data folder (the one the dev run above uses, never your real data) and fills it with a realistic pilot history: several wings (one retired), a harness, reserves, check reports, planned checks, gear in every reminder state and a few imported tracks. Dates are relative to today, so the reminders look right whenever you run it. The code lives in `app-desktop/src/jvmDev` and is not part of the shipped application. The shared run configuration **seed dev data** does the same from IntelliJ.

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

1. Make sure the user-facing changes are listed under `Unreleased` in `CHANGELOG.md` and that `./gradlew check` passes on `main`.
2. Run the **Release** workflow from the Actions tab on `main` and enter the version (or `gh workflow run release.yml -f version=X.Y.Z`).
3. The workflow dates the `Unreleased` section as the new version and starts a fresh one, builds and checks the Linux package, and only then commits the changelog, creates the `vX.Y.Z` tag and publishes a GitHub Release with that version's changelog section. It fails, changing nothing, when `Unreleased` is empty, the version already exists, or the build fails.

To rehearse a release, tick **dry run** (`-f dry_run=true`): the package is built and uploaded as a workflow artifact, and nothing is committed, tagged or published.

To build a package locally: `./gradlew :app-desktop:packageDeb -PappVersion=X.Y.Z`; the result is under `app-desktop/build/compose/binaries`.

## Application icon
The icon is drawn by `design/generate-icon.py` (needs Python and Pillow) and written to `app-desktop/icons` (installer) and `app-desktop/src/jvmMain/resources` (window and notification). Change the geometry in the script and run `python3 design/generate-icon.py` to regenerate every size; do not edit the images by hand.

