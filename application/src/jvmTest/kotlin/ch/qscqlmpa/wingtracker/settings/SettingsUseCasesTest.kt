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
}
