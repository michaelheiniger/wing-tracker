plugins {
    id("wingtracker.kmp-module")
}

// Pure Kotlin: no framework dependency may be added here (ADR 0004).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
    }
}
