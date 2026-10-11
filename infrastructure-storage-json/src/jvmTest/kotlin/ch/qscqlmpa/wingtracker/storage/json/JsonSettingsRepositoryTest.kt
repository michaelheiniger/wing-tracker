package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.ThemeMode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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
        fun `restores the reminder settings and the last notification day`() =
            runTest {
                // given
                val saved =
                    AppSettings(
                        themeMode = ThemeMode.Light,
                        reminderLeadTimes = ReminderLeadTimes(days = 14, hours = 5.hours + 30.minutes),
                        lastReminderNotificationOn = LocalDate(2026, 10, 10),
                        interfaceScalePercent = 150,
                    )
                repository.save(saved)

                // when
                val settings = repository.load()

                // then
                assertThat(settings).usingRecursiveComparison().isEqualTo(saved)
            }

        @Test
        fun `reads a settings file written before the interface size existed`() =
            runTest {
                // given
                dataDirectory.resolve("settings.json").writeText("""{"schemaVersion": 2, "themeMode": "Light"}""")

                // when
                val settings = repository.load()

                // then
                assertThat(settings.interfaceScalePercent).isNull()
                assertThat(settings.themeMode).isEqualTo(ThemeMode.Light)
            }

        @Test
        fun `quarantines a settings file with an interface size the domain rejects`() =
            runTest {
                // given
                dataDirectory.resolve("settings.json").writeText("""{"schemaVersion": 3, "interfaceScalePercent": 5}""")

                // when
                val settings = repository.load()

                // then
                assertThat(settings).isEqualTo(AppSettings())
                assertThat(dataDirectory.resolve("quarantine/settings.json").exists()).isTrue()
            }

        @Test
        fun `reads a settings file written before reminders existed`() =
            runTest {
                // given
                dataDirectory.resolve("settings.json").writeText("""{"schemaVersion": 1, "themeMode": "Dark"}""")

                // when
                val settings = repository.load()

                // then
                assertThat(settings).isEqualTo(AppSettings(themeMode = ThemeMode.Dark))
            }

        @Test
        fun `quarantines a settings file with a lead time the domain rejects`() =
            runTest {
                // given
                dataDirectory.resolve("settings.json").writeText("""{"schemaVersion": 2, "reminderLeadDays": 0}""")

                // when
                val settings = repository.load()

                // then
                assertThat(settings).isEqualTo(AppSettings())
                assertThat(dataDirectory.resolve("quarantine/settings.json").exists()).isTrue()
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
