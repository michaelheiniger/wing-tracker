plugins {
    id("wingtracker.kmp-module")
}

// JVM-only: it uses the JDK's HTTP client. Everything above it sees only the SyrideFlightSource port (ADR 0021).
kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":application"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlin.logging)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
