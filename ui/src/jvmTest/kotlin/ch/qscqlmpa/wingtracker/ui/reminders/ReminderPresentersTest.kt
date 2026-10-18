package ch.qscqlmpa.wingtracker.ui.reminders

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.backup.BackupReminder
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGear
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGearRepository
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.reminders.GetAttention
import ch.qscqlmpa.wingtracker.reminders.GetCalendarEntries
import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.GetAppSettings
import ch.qscqlmpa.wingtracker.settings.SaveBackupReminder
import ch.qscqlmpa.wingtracker.settings.SaveReminderLeadTimes
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.FileSaver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private fun aWing(
    id: String,
    name: String,
    inService: LocalDate,
) = Wing(
    id = WingId(id),
    details =
        WingDetails(
            name = name,
            manufacturer = "Advance",
            model = "Iota 3",
            acquisition = Acquisition(AcquisitionCondition.New),
            inServiceDate = inService,
            maintenanceInterval = MaintenanceInterval(months = 12),
        ),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderPresentersTest {
    private val gear = mockk<MaintainedGearRepository>()
    private val flights = mockk<FlightRepository>().also { coEvery { it.findAll() } returns LoadResult(emptyList()) }
    private val settings = mockk<SettingsRepository>(relaxUnitFun = true)
    private val saver = mockk<FileSaver>()
    private val clock =
        AppClock(
            clock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("2026-10-11T18:30:00Z")
                },
            zone = TimeZone.UTC,
        )

    private fun givenStored(vararg items: MaintainedGear) {
        coEvery { gear.findAll() } returns items.toList()
        coEvery { settings.load() } returns AppSettings()
    }

    private fun TestScope.useCases() =
        RemindersUseCases(
            getAttention = GetAttention(gear, flights, settings, clock),
            getAppSettings = GetAppSettings(settings),
            saveReminderLeadTimes = SaveReminderLeadTimes(settings),
            getCalendarEntries = GetCalendarEntries(gear, settings, clock),
            saveBackupReminder = SaveBackupReminder(settings),
        )

    // Words the calendar without resource files, which unit tests do not have.
    private val wording =
        CalendarWording(
            describe = { IcsEvent(it.uid, it.date, "${it.gearName} due") },
            calendarName = { "Wing Tracker" },
            dialogTitle = { "Save calendar" },
        )

    private fun TestScope.settingsPresenter(onSaved: () -> Unit = {}) =
        ReminderSettingsPresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            useCases(),
            saver,
            wording,
            onSaved,
        )

    @Nested
    inner class Attention {
        @Test
        fun `loads what needs attention`() =
            runTest {
                // given
                givenStored(aWing("w-1", "Overdue", LocalDate(2025, 3, 1)))
                val presenter =
                    AttentionPresenter(TestScope(UnconfinedTestDispatcher(testScheduler)), useCases().getAttention)

                // when
                presenter.refresh()

                // then
                assertThat(presenter.state.attention.count).isEqualTo(1)
                assertThat(presenter.state.failure).isNull()
            }

        @Test
        fun `shows a failure instead of crashing when loading fails`() =
            runTest {
                // given
                coEvery { gear.findAll() } throws IOException("disk unreadable")
                coEvery { settings.load() } returns AppSettings()
                val presenter =
                    AttentionPresenter(TestScope(UnconfinedTestDispatcher(testScheduler)), useCases().getAttention)

                // when
                presenter.refresh()

                // then
                assertThat(presenter.state.failure).isEqualTo(Failure(FailureKind.LoadAttention, "disk unreadable"))
            }
    }

    @Nested
    inner class Settings {
        @Test
        fun `loads the stored lead times into the form`() =
            runTest {
                // given
                coEvery { settings.load() } returns
                    AppSettings(reminderLeadTimes = ReminderLeadTimes(days = 14, hours = 5.hours))
                val presenter = settingsPresenter()

                // when
                presenter.load()

                // then
                assertThat(presenter.input).isEqualTo(ReminderSettingsInput(days = "14", hours = "5:00"))
            }

        @Test
        fun `saves valid lead times once typed and tells the app so reminders can refresh`() =
            runTest {
                // given
                coEvery { settings.load() } returns AppSettings()
                var refreshed = false
                val presenter = settingsPresenter { refreshed = true }

                // when
                presenter.change { ReminderSettingsInput(days = "21", hours = "8") }
                advanceUntilIdle()

                // then
                coVerify(
                    exactly = 1,
                ) { settings.save(AppSettings(reminderLeadTimes = ReminderLeadTimes(days = 21, hours = 8.hours))) }
                assertThat(presenter.saved).isTrue()
                assertThat(refreshed).isTrue()
            }

        @Test
        fun `saves the backup reminder with the lead times`() =
            runTest {
                // given
                coEvery { settings.load() } returns AppSettings()
                val presenter = settingsPresenter()

                // when
                presenter.change {
                    ReminderSettingsInput(days = "30", hours = "10", backupEnabled = false, backupDays = "60")
                }
                advanceUntilIdle()

                // then
                coVerify(exactly = 1) {
                    settings.save(AppSettings(backupReminder = BackupReminder(enabled = false, days = 60)))
                }
            }

        @Test
        fun `does not save invalid lead times`() =
            runTest {
                // given
                val presenter = settingsPresenter()

                // when
                presenter.change { ReminderSettingsInput(days = "0", hours = "8") }
                advanceUntilIdle()

                // then
                assertThat(presenter.errors).containsKey(ReminderSettingsField.Days)
                assertThat(presenter.saved).isFalse()
                coVerify(exactly = 0) { settings.save(any()) }
            }

        @Test
        fun `editing the form clears the saved confirmation`() =
            runTest {
                // given
                coEvery { settings.load() } returns AppSettings()
                val presenter = settingsPresenter()
                presenter.change { ReminderSettingsInput(days = "21", hours = "8") }
                advanceUntilIdle()

                // when
                presenter.change { it.copy(days = "22") }

                // then
                assertThat(presenter.saved).isFalse()
            }

        @Test
        fun `saves only the last of values typed in quick succession`() =
            runTest {
                // given
                coEvery { settings.load() } returns AppSettings()
                val presenter = settingsPresenter()

                // when
                presenter.change { ReminderSettingsInput(days = "2", hours = "8") }
                presenter.change { it.copy(days = "21") }
                advanceUntilIdle()

                // then
                coVerify(exactly = 0) { settings.save(match { it.reminderLeadTimes.days == 2 }) }
                coVerify(
                    exactly = 1,
                ) { settings.save(AppSettings(reminderLeadTimes = ReminderLeadTimes(days = 21, hours = 8.hours))) }
            }

        @Test
        fun `a value made invalid before the save drops the save`() =
            runTest {
                // given
                coEvery { settings.load() } returns AppSettings()
                val presenter = settingsPresenter()

                // when
                presenter.change { ReminderSettingsInput(days = "21", hours = "8") }
                presenter.change { it.copy(days = "") }
                advanceUntilIdle()

                // then
                assertThat(presenter.errors).containsOnlyKeys(ReminderSettingsField.Days)
                coVerify(exactly = 0) { settings.save(any()) }
            }
    }

    @Nested
    inner class CalendarExportBehaviour {
        @Test
        fun `reports that there is nothing to export`() =
            runTest {
                // given
                givenStored(aWing("w-1", "Overdue", LocalDate(2025, 3, 1)))
                val presenter = settingsPresenter()

                // when
                presenter.exportCalendar()

                // then
                assertThat(presenter.export).isEqualTo(ExportOutcome.NothingToExport)
            }

        @Test
        fun `saves the calendar file under the chosen name`() =
            runTest {
                // given
                givenStored(aWing("w-1", "Fine", LocalDate(2026, 6, 1)))
                coEvery { saver.save("Save calendar", "wing-tracker.ics", any()) } returns "my-gear.ics"
                val presenter = settingsPresenter()

                // when
                presenter.exportCalendar()

                // then
                assertThat(presenter.export).isEqualTo(ExportOutcome.Saved("my-gear.ics"))
                coVerify(
                    exactly = 1,
                ) { saver.save("Save calendar", "wing-tracker.ics", match { String(it).contains("BEGIN:VEVENT") }) }
            }

        @Test
        fun `says nothing when the save dialog is cancelled`() =
            runTest {
                // given
                givenStored(aWing("w-1", "Fine", LocalDate(2026, 6, 1)))
                coEvery { saver.save(any(), any(), any()) } returns null
                val presenter = settingsPresenter()

                // when
                presenter.exportCalendar()

                // then
                assertThat(presenter.export).isNull()
                assertThat(presenter.failure).isNull()
            }

        @Test
        fun `shows a failure when the file cannot be written`() =
            runTest {
                // given
                givenStored(aWing("w-1", "Fine", LocalDate(2026, 6, 1)))
                coEvery { saver.save(any(), any(), any()) } throws IOException("Disk full")
                val presenter = settingsPresenter()

                // when
                presenter.exportCalendar()

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.ExportCalendar, "Disk full"))
            }
    }
}
