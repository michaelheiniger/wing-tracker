plugins {
    id("wingtracker.kmp-module")
}

// JVM-only: it uses JGit. Everything above it sees only the LogbookSync and LogbookTransactions ports (ADR 0033).
kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":application"))
            implementation(project(":infrastructure-http"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlin.logging)
            implementation(libs.jgit)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
