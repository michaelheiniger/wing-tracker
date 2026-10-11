package ch.qscqlmpa.wingtracker.gear.wing.maintenance

import ch.qscqlmpa.wingtracker.gear.wing.aCheck
import ch.qscqlmpa.wingtracker.gear.wing.aWing
import ch.qscqlmpa.wingtracker.gear.wing.aWingDetails
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val IN_SERVICE = LocalDate(2024, 3, 9)

private fun aWingWith(
    interval: MaintenanceInterval?,
    vararg checks: MaintenanceCheck,
) = aWing(
    details = aWingDetails(inServiceDate = IN_SERVICE, hoursBeforeTracking = 42.hours, maintenanceInterval = interval),
    checks = checks.toList(),
)

private fun flown(
    date: LocalDate,
    duration: Duration,
) = FlownHours(date, duration)

class MaintenanceStatusTest {
    @Nested
    inner class WithoutRule {
        @Test
        fun `is not reported when the wing has no interval`() {
            // given
            val wing = aWingWith(interval = null)

            // when
            val status = wing.maintenanceStatus(emptyList(), LocalDate(2030, 1, 1))

            // then
            assertThat(status).isNull()
        }
    }

    @Nested
    inner class ByMonths {
        private val wing = aWingWith(MaintenanceInterval(months = 12))

        @Test
        fun `counts from the in-service date when no check was recorded`() {
            // given / when
            val status = wing.maintenanceStatus(emptyList(), LocalDate(2024, 6, 1))!!

            // then
            assertThat(status.since).isEqualTo(IN_SERVICE)
            assertThat(status.dueDate).isEqualTo(LocalDate(2025, 3, 9))
            assertThat(status.isDue).isFalse()
        }

        @Test
        fun `is not due the day before the due date`() {
            // given / when
            val status = wing.maintenanceStatus(emptyList(), LocalDate(2025, 3, 8))!!

            // then
            assertThat(status.isDue).isFalse()
        }

        @Test
        fun `is due on the due date`() {
            // given / when
            val status = wing.maintenanceStatus(emptyList(), LocalDate(2025, 3, 9))!!

            // then
            assertThat(status.isDue).isTrue()
        }

        @Test
        fun `counts from the last periodic check instead`() {
            // given
            val checked = aWingWith(MaintenanceInterval(months = 12), aCheck(date = LocalDate(2025, 4, 10)))

            // when
            val status = checked.maintenanceStatus(emptyList(), LocalDate(2025, 12, 1))!!

            // then
            assertThat(status.since).isEqualTo(LocalDate(2025, 4, 10))
            assertThat(status.dueDate).isEqualTo(LocalDate(2026, 4, 10))
            assertThat(status.isDue).isFalse()
        }

        @Test
        fun `ignores an other check that does not restart the interval`() {
            // given
            val checked =
                aWingWith(
                    MaintenanceInterval(months = 12),
                    aCheck(date = LocalDate(2025, 4, 10), type = WingCheckType.Other),
                )

            // when
            val status = checked.maintenanceStatus(emptyList(), LocalDate(2025, 12, 1))!!

            // then
            assertThat(status.since).isEqualTo(IN_SERVICE)
        }

        @Test
        fun `clamps to the end of a shorter month`() {
            // given
            val checked = aWingWith(MaintenanceInterval(months = 12), aCheck(date = LocalDate(2024, 2, 29)))

            // when
            val status = checked.maintenanceStatus(emptyList(), LocalDate(2025, 1, 1))!!

            // then
            assertThat(status.dueDate).isEqualTo(LocalDate(2025, 2, 28))
        }

        @Test
        fun `has no hours remaining when the rule only has months`() {
            // given / when
            val status = wing.maintenanceStatus(emptyList(), LocalDate(2024, 6, 1))!!

            // then
            assertThat(status.hoursRemaining).isNull()
        }
    }

    @Nested
    inner class ByHours {
        private val wing = aWingWith(MaintenanceInterval(hours = 100.hours))

        @Test
        fun `counts tracked flights since the in-service date without the pre-tracking hours`() {
            // given
            val flights = listOf(flown(LocalDate(2024, 5, 1), 30.hours), flown(LocalDate(2024, 6, 1), 20.hours))

            // when
            val status = wing.maintenanceStatus(flights, LocalDate(2024, 7, 1))!!

            // then
            assertThat(status.hoursSince).isEqualTo(50.hours)
            assertThat(status.hoursRemaining).isEqualTo(50.hours)
            assertThat(status.isDue).isFalse()
        }

        @Test
        fun `is due when the hours limit is reached exactly`() {
            // given
            val flights = listOf(flown(LocalDate(2024, 5, 1), 100.hours))

            // when
            val status = wing.maintenanceStatus(flights, LocalDate(2024, 7, 1))!!

            // then
            assertThat(status.isDue).isTrue()
            assertThat(status.hoursRemaining).isEqualTo(Duration.ZERO)
        }

        @Test
        fun `is due when the hours limit is exceeded and never reports negative hours remaining`() {
            // given
            val flights = listOf(flown(LocalDate(2024, 5, 1), 130.hours))

            // when
            val status = wing.maintenanceStatus(flights, LocalDate(2024, 7, 1))!!

            // then
            assertThat(status.isDue).isTrue()
            assertThat(status.hoursRemaining).isEqualTo(Duration.ZERO)
        }

        @Test
        fun `counts flights on the day of the last periodic check as flown after it`() {
            // given
            val checked = aWingWith(MaintenanceInterval(hours = 100.hours), aCheck(date = LocalDate(2025, 4, 10)))
            val flights = listOf(flown(LocalDate(2025, 4, 10), 5.hours))

            // when
            val status = checked.maintenanceStatus(flights, LocalDate(2025, 6, 1))!!

            // then
            assertThat(status.hoursSince).isEqualTo(5.hours)
        }

        @Test
        fun `counts flights on the in-service date when no check was recorded`() {
            // given
            val flights = listOf(flown(IN_SERVICE, 3.hours))

            // when
            val status = wing.maintenanceStatus(flights, LocalDate(2024, 7, 1))!!

            // then
            assertThat(status.hoursSince).isEqualTo(3.hours)
        }

        @Test
        fun `only counts flights from the day of the last periodic check onwards`() {
            // given
            val checked = aWingWith(MaintenanceInterval(hours = 100.hours), aCheck(date = LocalDate(2025, 4, 10)))
            val flights =
                listOf(
                    flown(LocalDate(2025, 1, 1), 80.hours),
                    flown(LocalDate(2025, 4, 10), 5.hours),
                    flown(LocalDate(2025, 5, 1), 10.hours + 30.minutes),
                )

            // when
            val status = checked.maintenanceStatus(flights, LocalDate(2025, 6, 1))!!

            // then
            assertThat(status.hoursSince).isEqualTo(15.hours + 30.minutes)
        }
    }

    @Nested
    inner class ByMonthsAndHours {
        private val wing = aWingWith(MaintenanceInterval(months = 24, hours = 100.hours))

        @Test
        fun `is due by hours even though the months have not passed`() {
            // given
            val flights = listOf(flown(LocalDate(2024, 5, 1), 100.hours))

            // when
            val status = wing.maintenanceStatus(flights, LocalDate(2024, 7, 1))!!

            // then
            assertThat(status.isDue).isTrue()
        }

        @Test
        fun `is due by months even though the hours were not reached`() {
            // given
            val flights = listOf(flown(LocalDate(2024, 5, 1), 10.hours))

            // when
            val status = wing.maintenanceStatus(flights, LocalDate(2026, 3, 9))!!

            // then
            assertThat(status.isDue).isTrue()
        }

        @Test
        fun `is not due when neither limit is reached`() {
            // given
            val flights = listOf(flown(LocalDate(2024, 5, 1), 10.hours))

            // when
            val status = wing.maintenanceStatus(flights, LocalDate(2025, 1, 1))!!

            // then
            assertThat(status.isDue).isFalse()
        }
    }
}
