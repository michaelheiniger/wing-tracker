package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.updates.AppVersion
import ch.qscqlmpa.wingtracker.updates.UpdatePreferences
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

class JsonUpdatePreferencesRepositoryTest {
    @TempDir
    lateinit var installationDirectory: Path

    private val repository by lazy { JsonUpdatePreferencesRepository(installationDirectory) }

    @Nested
    inner class Load {
        @Test
        fun `returns the defaults when nothing was saved yet`() =
            runTest {
                // when
                val preferences = repository.load()

                // then
                assertThat(preferences).isEqualTo(UpdatePreferences(checkAtStartup = true, skippedVersion = null))
            }

        @Test
        fun `restores the startup choice and the skipped version`() =
            runTest {
                // given
                val saved = UpdatePreferences(checkAtStartup = false, skippedVersion = AppVersion(0, 1, 0))
                repository.save(saved)

                // when
                val preferences = repository.load()

                // then
                assertThat(preferences).isEqualTo(saved)
            }

        @Test
        fun `falls back to the defaults and quarantines a corrupt file`() =
            runTest {
                // given
                installationDirectory.resolve("updates.json").writeText("not json at all")

                // when
                val preferences = repository.load()

                // then
                assertThat(preferences).isEqualTo(UpdatePreferences())
                assertThat(installationDirectory.resolve("updates.json").exists()).isFalse()
                assertThat(installationDirectory.resolve("quarantine/updates.json").exists()).isTrue()
            }

        @Test
        fun `quarantines a file whose skipped version is not a version`() =
            runTest {
                // given
                installationDirectory
                    .resolve("updates.json")
                    .writeText("""{"schemaVersion": 1, "checkAtStartup": true, "skippedVersion": "soon"}""")

                // when
                val preferences = repository.load()

                // then
                assertThat(preferences).isEqualTo(UpdatePreferences())
                assertThat(installationDirectory.resolve("quarantine/updates.json").exists()).isTrue()
            }

        @Test
        fun `falls back to the defaults and keeps a file from a newer version in place`() =
            runTest {
                // given
                repository.save(UpdatePreferences(checkAtStartup = false))
                val file = installationDirectory.resolve("updates.json")
                file.rewriteAsNewerVersion()

                // when
                val preferences = repository.load()

                // then
                assertThat(preferences).isEqualTo(UpdatePreferences())
                assertThat(file.exists()).isTrue()
                assertThat(installationDirectory.resolve("quarantine").exists()).isFalse()
            }
    }
}
