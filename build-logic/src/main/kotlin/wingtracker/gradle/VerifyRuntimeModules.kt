package wingtracker.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Fails when the application uses a JDK module that its packaged runtime leaves out. Such a gap only shows in the
 * installed app, the first time the code needing the module runs, so it is checked at build time instead.
 */
@CacheableTask
abstract class VerifyRuntimeModules
    @Inject
    constructor(
        private val exec: ExecOperations,
    ) : DefaultTask() {
        /** The application's jars and those of its libraries. */
        @get:Classpath
        abstract val classpath: ConfigurableFileCollection

        @get:Input
        abstract val javaHome: Property<String>

        @get:Input
        abstract val jvmTarget: Property<String>

        /** The `release` file of the packaged runtime, which lists the modules it holds. */
        @get:InputFile
        @get:PathSensitive(PathSensitivity.NONE)
        abstract val runtimeRelease: RegularFileProperty

        /** The modules the application uses, so a passing run can be cached. */
        @get:OutputFile
        abstract val report: RegularFileProperty

        @TaskAction
        fun verify() {
            val required = requiredModules()
            val packaged = packagedModules()
            val missing = (required - packaged).sorted()
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "The packaged runtime lacks JDK modules the application uses: ${missing.joinToString()}. " +
                        "Add them to modules(...) in nativeDistributions of the compose.desktop block.",
                )
            }
            report.get().asFile.writeText(required.sorted().joinToString("\n", postfix = "\n"))
        }

        private fun requiredModules(): Set<String> {
            val jars = classpath.files.filter { it.isFile }.map { it.absolutePath }
            val output = ByteArrayOutputStream()
            val errors = ByteArrayOutputStream()
            val result =
                exec.exec {
                    executable = File(javaHome.get(), "bin/jdeps").absolutePath
                    args("--print-module-deps", "--ignore-missing-deps", "--multi-release", jvmTarget.get())
                    args("--class-path", jars.joinToString(File.pathSeparator))
                    args(jars)
                    standardOutput = output
                    // Split-package warnings about libraries are noise here; they only matter when jdeps fails.
                    errorOutput = errors
                    isIgnoreExitValue = true
                }
            if (result.exitValue != 0) throw GradleException("jdeps failed:\n$errors")
            return output
                .toString()
                .lines()
                .last { it.isNotBlank() }
                .split(',')
                .map { it.trim() }
                .toSet()
        }

        private fun packagedModules(): Set<String> =
            runtimeRelease
                .get()
                .asFile
                .readLines()
                .first { it.startsWith("MODULES=") }
                .substringAfter('=')
                .trim('"')
                .split(' ')
                .filter { it.isNotBlank() }
                .toSet()
    }
