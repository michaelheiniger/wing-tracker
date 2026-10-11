plugins {
    id("wingtracker.kmp-module")
}

kotlin {
    sourceSets {
        jvmTest.dependencies {
            implementation(libs.konsist)
        }
    }
}

// The rules read other modules' sources, so changes there must invalidate the test result.
tasks.withType<Test>().configureEach {
    inputs
        .files(rootProject.fileTree(rootDir) { include("*/src/**/*.kt") })
        .withPropertyName("analysedSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
