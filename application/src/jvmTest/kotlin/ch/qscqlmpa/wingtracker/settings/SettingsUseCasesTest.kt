package ch.qscqlmpa.wingtracker.settings

import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

class SettingsUseCasesTest {
    private val settings = mockk<SettingsRepository>(relaxUnitFun = true)
    private val stored =
        AppSettings(
            themeMode = ThemeMode.Dark,
            reminderLeadTimes = ReminderLeadTimes(days = 14, hours = 5.hours),
            lastReminderNotificationOn = LocalDate(2026, 10, 10),
        )

    @Nested
    inner class SavingTheTheme {
        @Test
        fun `changes the theme and keeps the reminder settings`() =
            runTest {
                // given
                coEvery { settings.load() } returns stored

                // when
                SaveThemeMode(settings)(ThemeMode.Light)

                // then
                coVerify(exactly = 1) { settings.save(stored.copy(themeMode = ThemeMode.Light)) }
            }
    }

    @Nested
    inner class SavingTheLeadTimes {
        @Test
        fun `changes the lead times and keeps the theme and the last notification day`() =
            runTest {
                // given
                coEvery { settings.load() } returns stored
                val newLeadTimes = ReminderLeadTimes(days = 45, hours = 20.hours)

                // when
                SaveReminderLeadTimes(settings)(newLeadTimes)

                // then
                coVerify(exactly = 1) { settings.save(stored.copy(reminderLeadTimes = newLeadTimes)) }
            }
    }

    @Nested
    inner class SavingTheAirtimeNick {
        @Test
        fun `keeps the nick without surrounding spaces and the other settings`() =
            runTest {
                // given
                coEvery { settings.load() } returns stored

                // when
                SaveAirtimeNick(settings)("  qscqlmpa ")

                // then
                coVerify(exactly = 1) { settings.save(stored.copy(airtimeNick = "qscqlmpa")) }
            }

        @Test
        fun `forgets the nick when it is cleared`() =
            runTest {
                // given
                coEvery { settings.load() } returns stored.copy(airtimeNick = "qscqlmpa")

                // when
                SaveAirtimeNick(settings)("  ")

                // then
                coVerify(exactly = 1) { settings.save(stored.copy(airtimeNick = null)) }
            }
    }

    @Nested
    inner class SavingTheDeviceFolder {
        @Test
        fun `changes the folder and keeps the other settings`() =
            runTest {
                // given
                coEvery { settings.load() } returns stored

                // when
                SaveDeviceFolder(settings)(" /run/media/michael/SYRIDE1 ")

                // then
                coVerify(exactly = 1) { settings.save(stored.copy(deviceFolder = "/run/media/michael/SYRIDE1")) }
            }
    }

    @Nested
    inner class SavingTheInterfaceScale {
        @Test
        fun `changes the size and keeps the other settings`() =
            runTest {
                // given
                coEvery { settings.load() } returns stored

                // when
                SaveInterfaceScale(settings)(150)

                // then
                coVerify(exactly = 1) { settings.save(stored.copy(interfaceScalePercent = 150)) }
            }

        @Test
        fun `goes back to following the desktop`() =
            runTest {
                // given
                coEvery { settings.load() } returns stored.copy(interfaceScalePercent = 150)

                // when
                SaveInterfaceScale(settings)(null)

                // then
                coVerify(exactly = 1) { settings.save(stored.copy(interfaceScalePercent = null)) }
            }
    }

    @Nested
    inner class SettingsValidation {
        @Test
        fun `rejects an interface size outside the supported range`() {
            org.assertj.core.api.Assertions
                .assertThatThrownBy { AppSettings(interfaceScalePercent = 10) }
                .isInstanceOf(IllegalArgumentException::class.java)
            org.assertj.core.api.Assertions
                .assertThatThrownBy { AppSettings(interfaceScalePercent = 900) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `accepts the limits and following the desktop`() {
            org.assertj.core.api.Assertions
                .assertThatCode {
                    AppSettings(interfaceScalePercent = AppSettings.MIN_SCALE_PERCENT)
                    AppSettings(interfaceScalePercent = AppSettings.MAX_SCALE_PERCENT)
                    AppSettings(interfaceScalePercent = null)
                }.doesNotThrowAnyException()
        }
    }
}
