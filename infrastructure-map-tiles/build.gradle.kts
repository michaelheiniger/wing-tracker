plugins {
    id("wingtracker.kmp-module")
}

// JVM-only: it uses the JDK's HTTP client and the file system. Everything above it sees only the MapTileSource port
// (ADR 0027).
kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":application"))
            implementation(project(":infrastructure-http"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlin.logging)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
