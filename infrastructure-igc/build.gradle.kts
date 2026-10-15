plugins {
    id("wingtracker.kmp-module")
}

// JVM-only by nature: the IGC library runs on the JVM. Everything above it sees only the TrackReader port (ADR 0018).
kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":application"))
            implementation(libs.igc4j)
            implementation(libs.kotlin.logging)
        }
    }
}
