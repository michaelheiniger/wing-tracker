import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("wingtracker.compose")
}

kotlin {
    sourceSets {
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(project(":ui"))
            implementation(project(":application"))
            implementation(project(":infrastructure-storage-json"))
            implementation(project(":infrastructure-igc"))
            implementation(libs.compose.material3)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

// Release version comes from the git tag via -PappVersion (ADR 0009).
val appVersion = providers.gradleProperty("appVersion").orElse("1.0.0")

compose.desktop {
    application {
        mainClass = "ch.qscqlmpa.wingtracker.desktop.MainKt"
        // Dev runs pass -Dwingtracker.dev=true; see the IntelliJ run configuration.
        jvmArgs += listOf("-Dwingtracker.dev=${providers.gradleProperty("wingtracker.dev").orElse("false").get()}")
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "wing-tracker"
            packageVersion = appVersion.get()
        }
    }
}
