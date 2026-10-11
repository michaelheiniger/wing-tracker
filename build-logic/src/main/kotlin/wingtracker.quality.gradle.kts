import dev.detekt.gradle.extensions.DetektExtension

plugins {
    id("dev.detekt")
    id("org.jetbrains.kotlinx.kover")
    id("com.diffplug.spotless")
}

private val libs = the<VersionCatalogsExtension>().named("libs")

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.findVersion("ktlint").get().requiredVersion)
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.findVersion("ktlint").get().requiredVersion)
    }
}

extensions.configure<DetektExtension> {
    buildUponDefaultConfig.set(true)
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom(files("src"))
}
