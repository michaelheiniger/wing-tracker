import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("wingtracker.quality")
}

private val libs = the<VersionCatalogsExtension>().named("libs")

group = "ch.qscqlmpa.wingtracker"

kotlin {
    // Desktop is the only target for now (ADR 0002); more targets are added here, not per module.
    jvmToolchain(21)
    jvm()

    compilerOptions {
        allWarningsAsErrors.set(true)
    }

    sourceSets {
        // JUnit 5, MockK and AssertJ are JVM-only, so tests live in jvmTest (ADR 0008).
        jvmTest.dependencies {
            implementation(project.dependencies.platform(libs.findLibrary("junit-bom").get()))
            implementation(libs.findLibrary("junit-jupiter").get())
            implementation(libs.findLibrary("mockk").get())
            implementation(libs.findLibrary("assertj-core").get())
            runtimeOnly(libs.findLibrary("junit-platform-launcher").get())
            // The code under test logs through the facade, which needs a backend (ADR 0023).
            runtimeOnly(libs.findLibrary("logback-classic").get())
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // One quiet configuration for every module, so tests never write to a pilot's log folder.
    val loggingConfiguration = rootProject.file("config/logging/logback-test.xml")
    inputs.file(loggingConfiguration).withPathSensitivity(PathSensitivity.NONE)
    systemProperty("logback.configurationFile", loggingConfiguration.absolutePath)
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
}
