plugins {
    id("wingtracker.kmp-module")
}

// JVM-only: it uses the JDK's zip and XML readers and a JVM library for Excel files. Everything above it sees only
// the SpreadsheetReader port (ADR 0022).
kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":application"))
            implementation(libs.fastexcel.reader)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlin.logging)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
