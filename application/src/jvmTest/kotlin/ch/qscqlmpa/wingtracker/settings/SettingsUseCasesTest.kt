package ch.qscqlmpa.wingtracker.settings

import ch.qscqlmpa.wingtracker.backup.BackupReminder
import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.RecordingTransactions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

class SettingsUseCasesTest {
    private val transactions = RecordingTransactions()
    private val logbookSettings = mockk<LogbookSettingsRepository>(relaxUnitFun = true)
    private val deviceSettings = mockk<DeviceSettingsRepository>(relaxUnitFun = true)
    private val storedLogbook =
        LogbookSettings(
            themeMode = ThemeMode.Dark,
            reminderLeadTimes = ReminderLeadTimes(days = 14, hours = 5.hours),
            syrideUsername = "michael.heiniger",
        )
    private val storedDevice =
        DeviceSettings(
            lastReminderNotificationOn = LocalDate(2026, 10, 10),
            interfaceScalePercent = 125,
        )

    @Nested
    inner class SavingTheTheme {
        @Test
        fun `records the setting as one change to the logbook`() =
            runTest {
                // given
                coEvery { logbookSettings.load() } returns storedLogbook

                // when
                SaveThemeMode(logbookSettings, transactions)(ThemeMode.Light)

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.ThemeModeChanged)
            }

        @Test
        fun `changes the theme and keeps the reminder settings`() =
            runTest {
                // given
                coEvery { logbookSettings.load() } returns storedLogbook

                // when
                SaveThemeMode(logbookSettings, transactions)(ThemeMode.Light)

                // then
                coVerify(exactly = 1) { logbookSettings.save(storedLogbook.copy(themeMode = ThemeMode.Light)) }
            }
    }

    @Nested
    inner class SavingTheLeadTimes {
        @Test
        fun `records the setting as one change to the logbook`() =
            runTest {
                // given
                coEvery { logbookSettings.load() } returns storedLogbook

                // when
                SaveReminderLeadTimes(logbookSettings, transactions)(ReminderLeadTimes(days = 45, hours = 20.hours))

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.ReminderLeadTimesChanged)
            }

        @Test
        fun `changes the lead times and keeps the theme`() =
            runTest {
                // given
                coEvery { logbookSettings.load() } returns storedLogbook
                val newLeadTimes = ReminderLeadTimes(days = 45, hours = 20.hours)

                // when
                SaveReminderLeadTimes(logbookSettings, transactions)(newLeadTimes)

                // then
                coVerify(exactly = 1) { logbookSettings.save(storedLogbook.copy(reminderLeadTimes = newLeadTimes)) }
            }
    }

    @Nested
    inner class SavingTheAirtimeNick {
        @Test
        fun `records the setting as one change to the logbook`() =
            runTest {
                // given
                coEvery { logbookSettings.load() } returns storedLogbook

                // when
                SaveAirtimeNick(logbookSettings, transactions)("qscqlmpa")

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.AirtimeNickChanged)
            }

        @Test
        fun `keeps the nick without surrounding spaces and the other settings`() =
            runTest {
                // given
                coEvery { logbookSettings.load() } returns storedLogbook

                // when
                SaveAirtimeNick(logbookSettings, transactions)("  qscqlmpa ")

                // then
                coVerify(exactly = 1) { logbookSettings.save(storedLogbook.copy(airtimeNick = "qscqlmpa")) }
            }

        @Test
        fun `forgets the nick when it is cleared`() =
            runTest {
                // given
                coEvery { logbookSettings.load() } returns storedLogbook.copy(airtimeNick = "qscqlmpa")

                // when
                SaveAirtimeNick(logbookSettings, transactions)("  ")

                // then
                coVerify(exactly = 1) { logbookSettings.save(storedLogbook.copy(airtimeNick = null)) }
            }
    }

    @Nested
    inner class SavingTheSyrideUsername {
        @Test
        fun `records the setting as one change to the logbook`() =
            runTest {
                // given
                coEvery { logbookSettings.load() } returns storedLogbook

                // when
                SaveSyrideUsername(logbookSettings, transactions)("michael")

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.SyrideUsernameChanged)
            }

        @Test
        fun `keeps the username without surrounding spaces and the other settings`() =
            runTest {
                // given
                coEvery { logbookSettings.load() } returns storedLogbook

                // when
                SaveSyrideUsername(logbookSettings, transactions)(" michael ")

                // then
                coVerify(exactly = 1) { logbookSettings.save(storedLogbook.copy(syrideUsername = "michael")) }
            }
    }

    @Nested
    inner class SavingTheDeviceFolder {
        @Test
        fun `changes the folder and keeps the other device settings`() =
            runTest {
                // given
                coEvery { deviceSettings.load() } returns storedDevice

                // when
                SaveDeviceFolder(deviceSettings)(" /run/media/michael/SYRIDE1 ")

                // then
                coVerify(exactly = 1) {
                    deviceSettings.save(storedDevice.copy(deviceFolder = "/run/media/michael/SYRIDE1"))
                }
            }
    }

    @Nested
    inner class SavingTheBackupReminder {
        @Test
        fun `changes the reminder and keeps the other device settings`() =
            runTest {
                // given
                coEvery { deviceSettings.load() } returns storedDevice
                val reminder = BackupReminder(enabled = false, days = 60)

                // when
                SaveBackupReminder(deviceSettings)(reminder)

                // then
                coVerify(exactly = 1) { deviceSettings.save(storedDevice.copy(backupReminder = reminder)) }
            }
    }

    @Nested
    inner class SavingTheInterfaceScale {
        @Test
        fun `changes the size and keeps the other device settings`() =
            runTest {
                // given
                coEvery { deviceSettings.load() } returns storedDevice

                // when
                SaveInterfaceScale(deviceSettings)(150)

                // then
                coVerify(exactly = 1) { deviceSettings.save(storedDevice.copy(interfaceScalePercent = 150)) }
            }

        @Test
        fun `goes back to following the desktop`() =
            runTest {
                // given
                coEvery { deviceSettings.load() } returns storedDevice

                // when
                SaveInterfaceScale(deviceSettings)(null)

                // then
                coVerify(exactly = 1) { deviceSettings.save(storedDevice.copy(interfaceScalePercent = null)) }
            }
    }

    @Nested
    inner class DeviceSettingsValidation {
        @Test
        fun `rejects an interface size outside the supported range`() {
            assertThatThrownBy { DeviceSettings(interfaceScalePercent = 10) }
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy { DeviceSettings(interfaceScalePercent = 900) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `accepts the limits and following the desktop`() {
            assertThatCode {
                DeviceSettings(interfaceScalePercent = DeviceSettings.MIN_SCALE_PERCENT)
                DeviceSettings(interfaceScalePercent = DeviceSettings.MAX_SCALE_PERCENT)
                DeviceSettings(interfaceScalePercent = null)
            }.doesNotThrowAnyException()
        }
    }
}
