package ch.qscqlmpa.wingtracker.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.imports
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Module dependencies already forbid most violations; these rules also catch framework leaks
 * through transitive dependencies, which Gradle cannot (ADR 0004, ADR 0008).
 */
class LayerDependencyTest {
    private val base = "ch.qscqlmpa.wingtracker"

    private fun importsOf(module: String) = Konsist.scopeFromDirectory("$module/src").files.imports

    private fun assertNoImportStartsWith(
        module: String,
        vararg forbidden: String,
    ) {
        val violations =
            importsOf(module)
                .map { it.name }
                .filter { name -> forbidden.any { name.startsWith(it) } }
        assertThat(violations).describedAs("forbidden imports in $module").isEmpty()
    }

    @Nested
    inner class Domain {
        @Test
        fun `does not depend on outer layers or frameworks`() {
            assertNoImportStartsWith(
                "domain",
                "$base.application",
                "$base.infrastructure",
                "$base.ui",
                "$base.desktop",
                "androidx.compose",
                "org.koin",
                "kotlinx.serialization",
            )
        }
    }

    @Nested
    inner class Application {
        @Test
        fun `does not depend on infrastructure, ui or frameworks`() {
            assertNoImportStartsWith(
                "application",
                "$base.infrastructure",
                "$base.ui",
                "$base.desktop",
                "androidx.compose",
                "org.koin",
            )
        }
    }

    @Nested
    inner class Ui {
        @Test
        fun `does not depend on infrastructure or the composition root`() {
            assertNoImportStartsWith("ui", "$base.infrastructure", "$base.desktop")
        }
    }

    @Nested
    inner class InfrastructureStorageJson {
        @Test
        fun `does not depend on ui or the composition root`() {
            assertNoImportStartsWith("infrastructure-storage-json", "$base.ui", "$base.desktop", "androidx.compose")
        }
    }

    @Nested
    inner class InfrastructureIgc {
        @Test
        fun `does not depend on ui, storage or the composition root`() {
            assertNoImportStartsWith(
                "infrastructure-igc",
                "$base.ui",
                "$base.desktop",
                "$base.storage",
                "androidx.compose",
            )
        }
    }
}
