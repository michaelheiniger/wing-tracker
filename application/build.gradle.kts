plugins {
    id("wingtracker.kmp-module")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
