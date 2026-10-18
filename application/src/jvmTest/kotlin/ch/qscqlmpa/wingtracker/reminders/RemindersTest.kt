package ch.qscqlmpa.wingtracker.reminders

import ch.qscqlmpa.wingtracker.backup.BackupDue
import ch.qscqlmpa.wingtracker.backup.BackupReminder
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.aFixedClock
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.DueState
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGear
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGearRepository
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheckId
import ch.qscqlmpa.wingtracker.gear.maintenance.ReminderLeadTimes
import ch.qscqlmpa.wingtracker.gear.maintenance.aHarnessWithInterval
import ch.qscqlmpa.wingtracker.gear.wing.aWing
import ch.qscqlmpa.wingtracker.gear.wing.aWingDetails
import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// "Today" is 2026-10-11 (see aFixedClock in the flights tests).
private fun planned(
    id: String,
    date: LocalDate,
) = PlannedCheck(PlannedCheckId(id), date, CheckType.Full, company = "Alpine Wing Service", notes = "Annual")

private fun aWingWithInterval(
    id: String,
    name: String,
    inService: LocalDate,
    months: Int? = 12,
    planned: List<PlannedCheck> = emptyList(),
    status: GearStatus = GearStatus.Active,
) = aWing(
    id = id,
    details =
        aWingDetails(
            name = name,
            inServiceDate = inService,
            maintenanceInterval =
                months?.let {
                    MaintenanceInterval(months = it)
                },
        ),
    plannedChecks = planned,
    status = status,
)

// Recent enough that the backup reminder stays quiet in tests about the gear.
private val RECENT_BACKUP = Instant.parse("2026-10-05T18:00:00Z")

class RemindersTest {
    private val gear = mockk<MaintainedGearRepository>()
    private val flights = mockk<FlightRepository>().also { coEvery { it.findAll() } returns LoadResult(emptyList()) }
    private val settings = mockk<SettingsRepository>(relaxUnitFun = true)
    private val clock = aFixedClock()

    private fun givenGear(
        vararg items: MaintainedGear,
        appSettings: AppSettings = AppSettings(lastBackupAt = RECENT_BACKUP),
    ) {
        coEvery { gear.findAll() } returns items.toList()
        coEvery { settings.load() } returns appSettings
    }

    private val overdue = aWingWithInterval("w-1", "Overdue", LocalDate(2025, 3, 1))
    private val soon = aWingWithInterval("w-2", "Soon", LocalDate(2025, 11, 1))
    private val fine = aWingWithInterval("w-3", "Fine", LocalDate(2026, 6, 1))

    @Nested
    inner class AttentionOverview {
        @Test
        fun `lists due gear first, then due soon, and leaves out gear that is fine`() =
            runTest {
                // given
                givenGear(fine, soon, overdue)

                // when
                val attention = GetAttention(gear, flights, settings, clock)()

                // then
                assertThat(attention.items.map { it.name to it.state })
                    .containsExactly("Overdue" to DueState.Due, "Soon" to DueState.DueSoon)
                assertThat(attention.dueCount).isEqualTo(1)
                assertThat(attention.dueSoonCount).isEqualTo(1)
            }

        @Test
        fun `includes fine gear when a check is planned within the lead time`() =
            runTest {
                // given
                val withPlan =
                    aWingWithInterval(
                        "w-4",
                        "Planned",
                        LocalDate(2026, 6, 1),
                        planned = listOf(planned("p-1", LocalDate(2026, 10, 25))),
                    )
                givenGear(withPlan)

                // when
                val attention = GetAttention(gear, flights, settings, clock)()

                // then
                val item = attention.items.single()
                assertThat(item.state).isEqualTo(DueState.Ok)
                assertThat(item.nextPlanned?.id?.value).isEqualTo("p-1")
            }

        @Test
        fun `ignores a check that is planned far in the future`() =
            runTest {
                // given
                val withPlan =
                    aWingWithInterval(
                        "w-4",
                        "Planned",
                        LocalDate(2026, 6, 1),
                        planned = listOf(planned("p-1", LocalDate(2027, 3, 1))),
                    )
                givenGear(withPlan)

                // when
                val attention = GetAttention(gear, flights, settings, clock)()

                // then
                assertThat(attention.isEmpty).isTrue()
            }

        @Test
        fun `leaves out retired gear`() =
            runTest {
                // given
                givenGear(
                    aWingWithInterval(
                        "w-5",
                        "Sold",
                        LocalDate(2025, 3, 1),
                        status = GearStatus.Retired(LocalDate(2026, 1, 1)),
                    ),
                )

                // when
                val attention = GetAttention(gear, flights, settings, clock)()

                // then
                assertThat(attention.isEmpty).isTrue()
            }

        @Test
        fun `applies the lead time from the settings`() =
            runTest {
                // given
                givenGear(
                    fine,
                    appSettings = AppSettings(reminderLeadTimes = ReminderLeadTimes(days = 365, hours = 10.hours)),
                )

                // when
                val attention = GetAttention(gear, flights, settings, clock)()

                // then
                assertThat(attention.items.single().state).isEqualTo(DueState.DueSoon)
            }

        @Test
        fun `includes harnesses and reserves`() =
            runTest {
                // given
                givenGear(aHarnessWithInterval())

                // when
                val attention = GetAttention(gear, flights, settings, clock)()

                // then
                assertThat(attention.items.single().kind).isEqualTo(GearKind.Harness)
                assertThat(attention.items.single().state).isEqualTo(DueState.Due)
            }
    }

    @Nested
    inner class StartNotificationRules {
        private fun notification() = GetStartNotification(GetAttention(gear, flights, settings, clock), settings, clock)

        @Test
        fun `summarises what needs attention`() =
            runTest {
                // given
                givenGear(overdue, soon)

                // when
                val result = notification()()

                // then
                assertThat(result?.dueCount).isEqualTo(1)
                assertThat(result?.dueSoonCount).isEqualTo(1)
                assertThat(result?.names).containsExactly("Overdue", "Soon")
            }

        @Test
        fun `stays silent when nothing needs attention`() =
            runTest {
                // given
                givenGear(fine)

                // when
                val result = notification()()

                // then
                assertThat(result).isNull()
            }

        @Test
        fun `stays silent when it was already shown today`() =
            runTest {
                // given
                givenGear(overdue, appSettings = AppSettings(lastReminderNotificationOn = LocalDate(2026, 10, 11)))

                // when
                val result = notification()()

                // then
                assertThat(result).isNull()
            }

        @Test
        fun `shows again on a later day`() =
            runTest {
                // given
                givenGear(overdue, appSettings = AppSettings(lastReminderNotificationOn = LocalDate(2026, 10, 10)))

                // when
                val result = notification()()

                // then
                assertThat(result).isNotNull()
            }

        @Test
        fun `remembers today as the day it was shown without touching other settings`() =
            runTest {
                // given
                coEvery { settings.load() } returns
                    AppSettings(reminderLeadTimes = ReminderLeadTimes(days = 14, hours = 5.hours))

                // when
                MarkStartNotificationShown(settings, clock)()

                // then
                coVerify(exactly = 1) {
                    settings.save(
                        AppSettings(
                            reminderLeadTimes = ReminderLeadTimes(days = 14, hours = 5.hours),
                            lastReminderNotificationOn = LocalDate(2026, 10, 11),
                        ),
                    )
                }
            }
    }

    @Nested
    inner class CalendarExport {
        @Test
        fun `contains planned checks and upcoming date-based due dates, soonest first and then by name`() =
            runTest {
                // given
                val withPlan =
                    aWingWithInterval(
                        "w-4",
                        "Planned",
                        LocalDate(2026, 6, 1),
                        planned = listOf(planned("p-1", LocalDate(2026, 12, 1))),
                    )
                givenGear(overdue, fine, withPlan)

                // when
                val export = GetCalendarEntries(gear, settings, clock)()

                // then
                assertThat(export.entries.map { it.uid to it.date }).containsExactly(
                    "planned-p-1@wing-tracker" to LocalDate(2026, 12, 1),
                    "due-wing-w-3@wing-tracker" to LocalDate(2027, 6, 1),
                    "due-wing-w-4@wing-tracker" to LocalDate(2027, 6, 1),
                )
            }

        @Test
        fun `leaves out due dates that already passed`() =
            runTest {
                // given
                givenGear(overdue)

                // when
                val export = GetCalendarEntries(gear, settings, clock)()

                // then
                assertThat(export.entries).isEmpty()
            }

        @Test
        fun `leaves out retired gear and limits without a date`() =
            runTest {
                // given
                val retired =
                    aWingWithInterval(
                        "w-5",
                        "Sold",
                        LocalDate(2026, 6, 1),
                        status = GearStatus.Retired(LocalDate(2026, 8, 1)),
                    )
                val hoursOnly =
                    aWing(
                        id = "w-6",
                        details = aWingDetails(maintenanceInterval = MaintenanceInterval(hours = 100.hours)),
                    )
                givenGear(retired, hoursOnly)

                // when
                val export = GetCalendarEntries(gear, settings, clock)()

                // then
                assertThat(export.entries).isEmpty()
            }

        @Test
        fun `carries the lead time and the moment of export`() =
            runTest {
                // given
                givenGear(
                    fine,
                    appSettings = AppSettings(reminderLeadTimes = ReminderLeadTimes(days = 21, hours = 10.hours)),
                )

                // when
                val export = GetCalendarEntries(gear, settings, clock)()

                // then
                assertThat(export.leadDays).isEqualTo(21)
                assertThat(export.generatedAt).isEqualTo(clock.now())
            }

        @Test
        fun `describes a planned check with its type, company and notes`() =
            runTest {
                // given
                val withPlan =
                    aWingWithInterval(
                        "w-4",
                        "Planned",
                        LocalDate(2026, 6, 1),
                        months = null,
                        planned = listOf(planned("p-1", LocalDate(2026, 12, 1))),
                    )
                givenGear(withPlan)

                // when
                val entry = GetCalendarEntries(gear, settings, clock)().entries.single()

                // then
                assertThat(entry.kind).isEqualTo(CalendarEntryKind.PlannedCheck)
                assertThat(entry.checkType).isEqualTo(CheckType.Full)
                assertThat(entry.company).isEqualTo("Alpine Wing Service")
                assertThat(entry.notes).isEqualTo("Annual")
            }
    }

    @Nested
    inner class BackupReminderInAttention {
        @Test
        fun `asks for a backup once the last one is as old as the pilot allows`() =
            runTest {
                // given
                givenGear(fine, appSettings = AppSettings(lastBackupAt = Instant.parse("2026-09-11T19:30:00Z")))

                // when
                val attention = GetAttention(gear, flights, settings, clock)()

                // then
                assertThat(attention.backup).isEqualTo(BackupDue(LocalDate(2026, 9, 11)))
                assertThat(attention.count).isZero()
                assertThat(attention.isEmpty).isFalse()
            }

        @Test
        fun `stays quiet the day before`() =
            runTest {
                // given
                givenGear(fine, appSettings = AppSettings(lastBackupAt = Instant.parse("2026-09-12T08:00:00Z")))

                // when
                val attention = GetAttention(gear, flights, settings, clock)()

                // then
                assertThat(attention.backup).isNull()
            }

        @Test
        fun `asks for a first backup once something is stored`() =
            runTest {
                // given
                givenGear(fine, appSettings = AppSettings())

                // when
                val attention = GetAttention(gear, flights, settings, clock)()

                // then
                assertThat(attention.backup).isEqualTo(BackupDue(lastMadeOn = null))
            }

        @Test
        fun `does not ask a fresh installation with nothing to lose`() =
            runTest {
                // given
                givenGear(appSettings = AppSettings())

                // when
                val attention = GetAttention(gear, flights, settings, clock)()

                // then
                assertThat(attention.isEmpty).isTrue()
            }

        @Test
        fun `does not ask when the pilot turned the reminder off`() =
            runTest {
                // given
                givenGear(fine, appSettings = AppSettings(backupReminder = BackupReminder(enabled = false)))

                // when
                val attention = GetAttention(gear, flights, settings, clock)()

                // then
                assertThat(attention.backup).isNull()
            }

        @Test
        fun `follows the days the pilot chose`() =
            runTest {
                // given
                givenGear(
                    fine,
                    appSettings =
                        AppSettings(
                            lastBackupAt = RECENT_BACKUP,
                            backupReminder = BackupReminder(enabled = true, days = 6),
                        ),
                )

                // when
                val attention = GetAttention(gear, flights, settings, clock)()

                // then
                assertThat(attention.backup).isEqualTo(BackupDue(LocalDate(2026, 10, 5)))
            }

        @Test
        fun `is part of the start-up notification even when all gear is fine`() =
            runTest {
                // given
                givenGear(fine, appSettings = AppSettings())
                val getAttention = GetAttention(gear, flights, settings, clock)

                // when
                val notification = GetStartNotification(getAttention, settings, clock)()

                // then
                assertThat(notification)
                    .isEqualTo(StartNotification(0, 0, 0, names = emptyList(), backupDue = true))
            }
    }
}
