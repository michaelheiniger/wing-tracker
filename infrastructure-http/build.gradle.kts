plugins {
    id("wingtracker.kmp-module")
}

// JVM-only plumbing shared by the adapters that go online; it knows no port, so they map its outcomes (ADR 0004).
kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
