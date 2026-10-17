# wing-tracker

Kotlin Multiplatform app to track paragliding gear (wings, harnesses, reserve parachutes), their maintenance history and reminders, and flights per wing. Desktop first; web and native mobile later. Long-term project: favour evolvability and maintainability over speed.

## How to work here
- Act as an expert software engineer. Use DDD and best practices, and suggest refactorings.
- **Do not assume or make judgment calls: ask.** When several solutions have trade-offs, explain them and ask for a decision. No shortcuts.
- **ADRs in `docs/adr/` are binding.** Check a request against them before implementing. If it contradicts an Accepted ADR, do not comply silently: name the ADR, explain the trade-off it protects, ask the user to reconsider or consciously supersede it. Amend vs supersede rules are in `docs/adr/README.md`. Suggest a new or amended ADR whenever a change is architecturally relevant.
- UI/UX matters a lot: suggest ideas with each feature and challenge the status quo; redesigning for better UX is encouraged.
- Commit each feature slice separately, once it is finished and `./gradlew check` passes. Do not commit half-done work or mix slices in one commit.
- Release notes: every user-facing change adds a line under `Unreleased` in `CHANGELOG.md`.

## Comments and docs
- Comments explain **why**, never how. Keep them concise.
- Never write the concrete value of a configuration property (versions, ports, paths, thresholds) in any doc: comments, Markdown, ADRs, READMEs, SQL files. Name the property and where it lives instead.

## Architecture
Modules by layer (ADR 0004); dependencies point inwards:
- `domain`: pure Kotlin, no framework. Aggregates, value objects, domain services, repository ports.
- `application`: use cases orchestrating the domain.
- `infrastructure-*`: adapters (e.g. `infrastructure-storage-json`). Storage is swappable (JSON files now; cloud/DB later).
- `ui`: shared Compose Multiplatform UI and theme. Gear is split into tabs per kind; maintenance screens are generic over `GearKind`.
- `app-desktop`: composition root (Koin wiring) and desktop entry point. The only module that sees everything.
- `infrastructure-igc`: JVM-only adapter for the `TrackReader` port (igc4j, ADR 0018).
- `infrastructure-syride`: JVM-only adapter for the `SyrideFlightSource` port; reads public Syride pages (ADR 0021).
- `infrastructure-spreadsheet`: JVM-only adapter for the `SpreadsheetReader` port; reads ODS and XLSX logbooks (ADR 0022).
- `infrastructure-github-releases`: JVM-only adapter for the `ReleaseSource` port; lists published releases for the built-in updater (ADR 0024).
- `architecture-test`: Konsist rules enforcing the layering.

Bounded contexts (gear, flights, maintenance, plus the supporting `updates`) are packages inside each module. Base package equals the Gradle `group` (ADR 0005). Domain and application never reference Koin, Compose or serialization frameworks.

Build logic lives in `build-logic` convention plugins (`wingtracker.kmp-module`, `wingtracker.compose`, `wingtracker.quality`). Dependency versions live only in `gradle/libs.versions.toml`.

## Commands
- All tests and checks: `./gradlew check`
- Fix formatting: `./gradlew spotlessApply`
- Run (dev): `./gradlew :app-desktop:run -Pwingtracker.dev=true`
- Fill the dev folder with sample data: `./gradlew seedDevData` (resets it first; code in `app-desktop/src/jvmDev`, never shipped)
- One class: `./gradlew :<module>:jvmTest --tests '<fully.qualified.ClassName>'`
- One case: `./gradlew :<module>:jvmTest --tests '<fully.qualified.ClassName>.<method name>'`
- Package: `./gradlew :app-desktop:packageDeb` (Linux) or `:app-desktop:packageMsi` (Windows)

## Tests
Test pyramid: many fast unit tests (domain, application), fewer integration tests (storage adapters; Testcontainers when a real DB/service is involved), few UI/end-to-end tests. Tests live in `jvmTest`.
- JUnit 5, MockK, AssertJ. Structure every test as `// given`, `// when`, `// then`.
- `@Nested inner class` per method under test; descriptive backtick names stating the scenario and expected result.
- Use `usingRecursiveComparison()` for complex objects, unless only a few attributes matter: then assert those.
- Realistic fixture values, consistent across the whole suite so that what differs between tests is what the test is about.
- Helper factory methods returning a fully populated valid object that tests tweak. Keep separate factories per area (domain, persistence, ...) so unrelated tests do not depend on each other.
- Detekt/ktlint run on tests too.

## Conventions
- `allWarningsAsErrors` is on; fix warnings, don't suppress them.
- Colours come from the theme (`MaterialTheme.colorScheme` and `MaterialTheme.extraColors`), never literals in screens (ADR 0019). State is shown with a badge that has an icon and text, not colour alone.
- Composable functions are PascalCase (ktlint and detekt are configured for it).
- Semantic Versioning, strictly x.y.z (ADR 0009).
- No user-visible text in code: strings live in Compose resources with one folder per locale (ADR 0014). Logic reports error kinds (enums, typed failures); only composables turn them into text.
- Unreadable stored data is quarantined, never deleted (ADR 0015). Repositories return `LoadResult`.
- Reminders are computed on demand from stored data; there is no background service (ADR 0012). Lead times live in the settings.
- Cross-aggregate rules (e.g. flight date versus wing service date) live in application use cases, not in aggregates.
