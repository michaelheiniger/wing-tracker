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
