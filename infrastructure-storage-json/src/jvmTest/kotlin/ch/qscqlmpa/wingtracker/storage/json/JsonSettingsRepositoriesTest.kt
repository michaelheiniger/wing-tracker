package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.backup.BackupReminder
import ch.qscqlmpa.wingtracker.backup.CloudProvider
import ch.qscqlmpa.wingtracker.backup.CloudStorageSettings
import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import ch.qscqlmpa.wingtracker.settings.DeviceSettings
import ch.qscqlmpa.wingtracker.settings.LogbookSettings
import ch.qscqlmpa.wingtracker.settings.ThemeMode
import ch.qscqlmpa.wingtracker.sync.SyncSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val LOGBOOK_FILE = "settings.json"
private const val DEVICE_FILE = "device-settings.json"

private val LOGBOOK =
    LogbookSettings(
        themeMode = ThemeMode.Light,
        reminderLeadTimes = ReminderLeadTimes(days = 14, hours = 5.hours + 30.minutes),
        syrideUsername = "michael.heiniger",
        airtimeNick = "qscqlmpa",
        cloudStorage =
            CloudStorageSettings(
                provider = CloudProvider.MegaS4,
                region = "eu-amsterdam-1",
                endpoint = null,
                bucket = "wing-tracker-backups",
                accessKeyId = "AKIAMEGAS4EXAMPLE",
                keepLast = 10,
            ),
    )

private val DEVICE =
    DeviceSettings(
        lastReminderNotificationOn = LocalDate(2026, 10, 10),
        interfaceScalePercent = 150,
        deviceFolder = "/run/media/michael/SYRIDE1",
        lastBackupAt = Instant.parse("2026-10-18T12:30:00Z"),
        backupReminder = BackupReminder(enabled = false, days = 60),
    )

// Only a device of today has these: the legacy file cannot hold them.
private val SYNCED_DEVICE =
    DEVICE.copy(
        sync =
            SyncSettings(
                repository = "https://codeberg.org/michael/logbook.git",
                username = "michael",
                authorName = "Michael Heiniger",
                authorEmail = "michael@example.org",
            ),
        lastSyncedAt = Instant.parse("2026-10-20T17:45:00Z"),
    )

// The same values as LOGBOOK and DEVICE, as the last version before the split wrote them.
private val LEGACY_SETTINGS =
    """
    {
        "schemaVersion": 7,
        "themeMode": "Light",
        "reminderLeadDays": 14,
        "reminderLeadHoursMinutes": 330,
        "lastReminderNotificationOn": "2026-10-10",
        "interfaceScalePercent": 150,
        "syrideUsername": "michael.heiniger",
        "deviceFolder": "/run/media/michael/SYRIDE1",
        "airtimeNick": "qscqlmpa",
        "lastBackupAt": "2026-10-18T12:30:00Z",
        "backupReminderEnabled": false,
        "backupReminderDays": 60,
        "cloudStorage": {
            "provider": "MegaS4",
            "region": "eu-amsterdam-1",
            "bucket": "wing-tracker-backups",
            "accessKeyId": "AKIAMEGAS4EXAMPLE",
            "keepLast": 10
        }
    }
    """.trimIndent()

class JsonSettingsRepositoriesTest {
    @TempDir
    lateinit var dataDirectory: Path

    private val logbookSettings by lazy { JsonLogbookSettingsRepository(dataDirectory) }
    private val deviceSettings by lazy { JsonDeviceSettingsRepository(dataDirectory) }

    @Nested
    inner class LogbookSettingsLoad {
        @Test
        fun `returns the defaults when nothing was saved yet`() =
            runTest {
                // given / when
                val settings = logbookSettings.load()

                // then
                assertThat(settings).isEqualTo(LogbookSettings())
            }

        @Test
        fun `restores everything saved`() =
            runTest {
                // given
                logbookSettings.save(LOGBOOK)

                // when
                val settings = logbookSettings.load()

                // then
                assertThat(settings).usingRecursiveComparison().isEqualTo(LOGBOOK)
            }

        @Test
        fun `keeps the device settings out of the logbook settings file`() =
            runTest {
                // given
                logbookSettings.save(LOGBOOK)
                deviceSettings.save(DEVICE)

                // when
                val text = dataDirectory.resolve(LOGBOOK_FILE).readText()

                // then
                assertThat(text).doesNotContain("interfaceScalePercent", "deviceFolder", "lastBackupAt")
            }

        @Test
        fun `falls back to the defaults and quarantines a corrupt file`() =
            runTest {
                // given
                dataDirectory.resolve(LOGBOOK_FILE).writeText("not json at all")

                // when
                val settings = logbookSettings.load()

                // then
                assertThat(settings).isEqualTo(LogbookSettings())
                assertThat(dataDirectory.resolve(LOGBOOK_FILE).exists()).isFalse()
                assertThat(dataDirectory.resolve("quarantine/$LOGBOOK_FILE").exists()).isTrue()
            }

        @Test
        fun `falls back to the defaults and keeps a file from a newer version in place`() =
            runTest {
                // given
                logbookSettings.save(LOGBOOK)
                val file = dataDirectory.resolve(LOGBOOK_FILE)
                file.rewriteAsNewerVersion()

                // when
                val settings = logbookSettings.load()

                // then
                assertThat(settings).isEqualTo(LogbookSettings())
                assertThat(file.exists()).isTrue()
                assertThat(dataDirectory.resolve("quarantine").exists()).isFalse()
            }

        @Test
        fun `quarantines a file with a lead time the domain rejects`() =
            runTest {
                // given
                dataDirectory.resolve(LOGBOOK_FILE).writeText("""{"schemaVersion": 8, "reminderLeadDays": 0}""")

                // when
                val settings = logbookSettings.load()

                // then
                assertThat(settings).isEqualTo(LogbookSettings())
                assertThat(dataDirectory.resolve("quarantine/$LOGBOOK_FILE").exists()).isTrue()
            }
    }

    @Nested
    inner class DeviceSettingsLoad {
        @Test
        fun `returns the defaults when nothing was saved yet`() =
            runTest {
                // given / when
                val settings = deviceSettings.load()

                // then
                assertThat(settings).isEqualTo(DeviceSettings())
            }

        @Test
        fun `restores everything saved`() =
            runTest {
                // given
                deviceSettings.save(SYNCED_DEVICE)

                // when
                val settings = deviceSettings.load()

                // then
                assertThat(settings).usingRecursiveComparison().isEqualTo(SYNCED_DEVICE)
            }

        @Test
        fun `falls back to the defaults and quarantines a corrupt file`() =
            runTest {
                // given
                dataDirectory.resolve(DEVICE_FILE).writeText("not json at all")

                // when
                val settings = deviceSettings.load()

                // then
                assertThat(settings).isEqualTo(DeviceSettings())
                assertThat(dataDirectory.resolve(DEVICE_FILE).exists()).isFalse()
                assertThat(dataDirectory.resolve("quarantine/$DEVICE_FILE").exists()).isTrue()
            }

        @Test
        fun `falls back to the defaults and keeps a file from a newer version in place`() =
            runTest {
                // given
                deviceSettings.save(DEVICE)
                val file = dataDirectory.resolve(DEVICE_FILE)
                file.rewriteAsNewerVersion()

                // when
                val settings = deviceSettings.load()

                // then
                assertThat(settings).isEqualTo(DeviceSettings())
                assertThat(file.exists()).isTrue()
                assertThat(dataDirectory.resolve("quarantine").exists()).isFalse()
            }

        @Test
        fun `quarantines a file with an interface size the domain rejects`() =
            runTest {
                // given
                dataDirectory.resolve(DEVICE_FILE).writeText("""{"schemaVersion": 1, "interfaceScalePercent": 5}""")

                // when
                val settings = deviceSettings.load()

                // then
                assertThat(settings).isEqualTo(DeviceSettings())
                assertThat(dataDirectory.resolve("quarantine/$DEVICE_FILE").exists()).isTrue()
            }
    }

    @Nested
    inner class DeviceSettingsSaveFor {
        @Test
        fun `writes the settings of another data folder, leaving this one alone`() =
            runTest {
                // given
                val other = dataDirectory.resolve("joined").also { it.toFile().mkdirs() }

                // when
                deviceSettings.saveFor(other.toString(), SYNCED_DEVICE)

                // then
                assertThat(
                    JsonDeviceSettingsRepository(other).load(),
                ).usingRecursiveComparison().isEqualTo(SYNCED_DEVICE)
                assertThat(dataDirectory.resolve(DEVICE_FILE).exists()).isFalse()
            }
    }

    @Nested
    inner class SplittingSettingsFromBeforeTheSplit {
        @Test
        fun `moves each setting to the logbook or the device`() =
            runTest {
                // given
                dataDirectory.resolve(LOGBOOK_FILE).writeText(LEGACY_SETTINGS)

                // when
                val logbook = logbookSettings.load()
                val device = deviceSettings.load()

                // then
                assertThat(logbook).usingRecursiveComparison().isEqualTo(LOGBOOK)
                assertThat(device).usingRecursiveComparison().isEqualTo(DEVICE)
                assertThat(dataDirectory.resolve(LOGBOOK_FILE).readText()).doesNotContain("deviceFolder")
            }

        @Test
        fun `splits the file also when the device settings are saved first`() =
            runTest {
                // given
                dataDirectory.resolve(LOGBOOK_FILE).writeText(LEGACY_SETTINGS)

                // when
                deviceSettings.save(DEVICE.copy(interfaceScalePercent = 125))

                // then
                assertThat(logbookSettings.load()).usingRecursiveComparison().isEqualTo(LOGBOOK)
                assertThat(deviceSettings.load().interfaceScalePercent).isEqualTo(125)
            }

        @Test
        fun `reads a file written before reminders existed`() =
            runTest {
                // given
                dataDirectory.resolve(LOGBOOK_FILE).writeText("""{"schemaVersion": 1, "themeMode": "Dark"}""")

                // when
                val logbook = logbookSettings.load()
                val device = deviceSettings.load()

                // then
                assertThat(logbook).isEqualTo(LogbookSettings(themeMode = ThemeMode.Dark))
                assertThat(device).isEqualTo(DeviceSettings())
            }

        @Test
        fun `keeps device settings a split interrupted before already wrote`() =
            runTest {
                // given
                dataDirectory.resolve(LOGBOOK_FILE).writeText(LEGACY_SETTINGS)
                dataDirectory
                    .resolve(DEVICE_FILE)
                    .writeText("""{"schemaVersion": 1, "interfaceScalePercent": 125}""")

                // when
                val logbook = logbookSettings.load()
                val device = deviceSettings.load()

                // then
                assertThat(logbook).usingRecursiveComparison().isEqualTo(LOGBOOK)
                assertThat(device).isEqualTo(DeviceSettings(interfaceScalePercent = 125))
            }

        @Test
        fun `quarantines the original file when it holds a value the domain rejects`() =
            runTest {
                // given
                val original = """{"schemaVersion": 3, "themeMode": "Dark", "interfaceScalePercent": 5}"""
                dataDirectory.resolve(LOGBOOK_FILE).writeText(original)

                // when
                val logbook = logbookSettings.load()
                val device = deviceSettings.load()

                // then
                assertThat(logbook).isEqualTo(LogbookSettings())
                assertThat(device).isEqualTo(DeviceSettings())
                assertThat(dataDirectory.resolve("quarantine/$LOGBOOK_FILE").readText()).isEqualTo(original)
                assertThat(dataDirectory.resolve(DEVICE_FILE).exists()).isFalse()
            }

        @Test
        fun `leaves a file from a newer version alone`() =
            runTest {
                // given
                logbookSettings.save(LOGBOOK)
                val file = dataDirectory.resolve(LOGBOOK_FILE)
                file.rewriteAsNewerVersion()
                val newer = file.readText()

                // when
                deviceSettings.load()

                // then
                assertThat(file.readText()).isEqualTo(newer)
                assertThat(dataDirectory.resolve(DEVICE_FILE).exists()).isFalse()
            }
    }
}
