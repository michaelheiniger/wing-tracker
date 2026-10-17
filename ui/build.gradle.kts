plugins {
    id("wingtracker.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":application"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.icons.core)
            implementation(libs.compose.resources)
            implementation(libs.kotlinx.coroutines.core)
            // Release notes are Markdown; confined to this module (ADR 0024).
            implementation(libs.markdown.renderer.m3)
            // The flight map (ADR 0027). Its desktop runtime names the macOS one of Compose; the platform's own comes
            // from the composition root, so this one would only bloat the packages.
            // Only the string notation takes the block that excludes a module.
            val mapcompose = libs.mapcompose.get()
            implementation("${mapcompose.module}:${mapcompose.version}") {
                exclude(group = "org.jetbrains.compose.desktop", module = "desktop-jvm-macos-arm64")
            }
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    packageOfResClass = "ch.qscqlmpa.wingtracker.ui.resources"
    generateResClass = always
}
