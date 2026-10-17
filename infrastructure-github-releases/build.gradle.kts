plugins {
    id("wingtracker.kmp-module")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// JVM-only: it uses the JDK's HTTP client. Everything above it sees only the ReleaseSource port (ADR 0024).
kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":application"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlin.logging)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
