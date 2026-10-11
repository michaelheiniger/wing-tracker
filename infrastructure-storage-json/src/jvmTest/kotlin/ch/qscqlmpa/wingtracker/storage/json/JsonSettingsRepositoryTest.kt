package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.ThemeMode
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

class JsonSettingsRepositoryTest {
    @TempDir
    lateinit var dataDirectory: Path

    private val repository by lazy { JsonSettingsRepository(dataDirectory) }

    @Nested
    inner class Load {
        @Test
        fun `falls back to the defaults and quarantines a corrupt settings file`() =
            runTest {
                // given
                dataDirectory.resolve("settings.json").writeText("not json at all")

                // when
                val settings = repository.load()

                // then
                assertThat(settings).isEqualTo(AppSettings())
                assertThat(dataDirectory.resolve("settings.json").exists()).isFalse()
                assertThat(dataDirectory.resolve("quarantine/settings.json").exists()).isTrue()
            }

        @Test
        fun `returns the defaults when no settings were saved yet`() =
            runTest {
                // given / when
                val settings = repository.load()

                // then
                assertThat(settings).isEqualTo(AppSettings(themeMode = ThemeMode.System))
            }

        @Test
        fun `restores the saved theme`() =
            runTest {
                // given
                repository.save(AppSettings(themeMode = ThemeMode.Dark))

                // when
                val settings = repository.load()

                // then
                assertThat(settings.themeMode).isEqualTo(ThemeMode.Dark)
            }
    }
}
