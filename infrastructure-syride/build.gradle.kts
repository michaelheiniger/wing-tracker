plugins {
    id("wingtracker.kmp-module")
}

// JVM-only: it uses the JDK's HTTP client. Everything above it sees only the SyrideFlightSource port (ADR 0021).
kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":application"))
            implementation(project(":infrastructure-http"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlin.logging)
            implementation(libs.jsoup)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
