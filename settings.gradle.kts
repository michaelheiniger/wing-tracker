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
    }
}

// Layer boundaries are enforced by module dependencies (ADR 0004).
include(
    ":domain",
    ":application",
    ":infrastructure-storage-json",
    ":ui",
    ":app-desktop",
    ":architecture-test",
)
