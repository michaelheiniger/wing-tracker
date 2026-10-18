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

    // Only the composition root picks the logging backend; everything else logs through the facade (ADR 0023).
    private val loggingBackends = arrayOf("org.slf4j", "ch.qos.logback")

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
                "io.github.oshai.kotlinlogging",
                *loggingBackends,
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
                *loggingBackends,
            )
        }
    }

    @Nested
    inner class Ui {
        @Test
        fun `does not depend on infrastructure or the composition root`() {
            assertNoImportStartsWith("ui", "$base.infrastructure", "$base.desktop", *loggingBackends)
        }
    }

    @Nested
    inner class InfrastructureStorageJson {
        @Test
        fun `does not depend on ui or the composition root`() {
            assertNoImportStartsWith(
                "infrastructure-storage-json",
                "$base.ui",
                "$base.desktop",
                "androidx.compose",
                *loggingBackends,
            )
        }
    }

    @Nested
    inner class InfrastructureHttp {
        @Test
        fun `knows nothing of the application, so any adapter can use it`() {
            val violations =
                importsOf("infrastructure-http")
                    .map { it.name }
                    .filter { it.startsWith("$base.") && !it.startsWith("$base.http.") }
            assertThat(violations).describedAs("project imports in infrastructure-http").isEmpty()
        }

        @Test
        fun `does not depend on ui or the composition root`() {
            assertNoImportStartsWith("infrastructure-http", "androidx.compose", "org.koin", *loggingBackends)
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
                *loggingBackends,
            )
        }
    }

    @Nested
    inner class InfrastructureSyride {
        @Test
        fun `does not depend on ui, storage or the composition root`() {
            assertNoImportStartsWith(
                "infrastructure-syride",
                "$base.ui",
                "$base.desktop",
                "$base.storage",
                "androidx.compose",
                *loggingBackends,
            )
        }
    }

    @Nested
    inner class InfrastructureAirtime {
        @Test
        fun `does not depend on ui, storage or the composition root`() {
            assertNoImportStartsWith(
                "infrastructure-airtime",
                "$base.ui",
                "$base.desktop",
                "$base.storage",
                "androidx.compose",
                *loggingBackends,
            )
        }
    }

    @Nested
    inner class InfrastructureS3 {
        @Test
        fun `does not depend on ui, storage or the composition root`() {
            assertNoImportStartsWith(
                "infrastructure-s3",
                "$base.ui",
                "$base.desktop",
                "$base.storage",
                "androidx.compose",
                *loggingBackends,
            )
        }
    }

    @Nested
    inner class InfrastructureSpreadsheet {
        @Test
        fun `does not depend on ui, storage or the composition root`() {
            assertNoImportStartsWith(
                "infrastructure-spreadsheet",
                "$base.ui",
                "$base.desktop",
                "$base.storage",
                "androidx.compose",
                *loggingBackends,
            )
        }
    }

    @Nested
    inner class InfrastructureGitHubReleases {
        @Test
        fun `does not depend on ui, storage or the composition root`() {
            assertNoImportStartsWith(
                "infrastructure-github-releases",
                "$base.ui",
                "$base.desktop",
                "$base.storage",
                "androidx.compose",
                *loggingBackends,
            )
        }
    }

    @Nested
    inner class InfrastructureMapTiles {
        @Test
        fun `does not depend on ui, storage or the composition root`() {
            assertNoImportStartsWith(
                "infrastructure-map-tiles",
                "$base.ui",
                "$base.desktop",
                "$base.storage",
                "androidx.compose",
                *loggingBackends,
            )
        }
    }
}
