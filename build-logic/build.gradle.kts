plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.compose)
    implementation(libs.gradle.compose.compiler)
    implementation(libs.gradle.serialization)
    implementation(libs.gradle.detekt)
    implementation(libs.gradle.kover)
    implementation(libs.gradle.spotless)
}
