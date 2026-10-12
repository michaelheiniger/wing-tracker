rootProject.name = "wing-tracker"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // igc4j is only published through JitPack. The repository is restricted to its one group, so nothing else
        // can ever be resolved from it (ADR 0018).
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.smirnovegorv") }
        }
    }
}

// Layer boundaries are enforced by module dependencies (ADR 0004).
include(
    ":domain",
    ":application",
    ":infrastructure-storage-json",
    ":infrastructure-igc",
    ":infrastructure-syride",
    ":ui",
    ":app-desktop",
    ":architecture-test",
)
