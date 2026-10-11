plugins {
    id("org.jetbrains.kotlinx.kover")
}

// One merged coverage report across all layers.
dependencies {
    subprojects
        .filter { it.name != "architecture-test" }
        .forEach { kover(it) }
}
