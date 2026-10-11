package ch.qscqlmpa.wingtracker.gear.maintenance

import ch.qscqlmpa.wingtracker.gear.wing.aCheck
import ch.qscqlmpa.wingtracker.gear.wing.aPlannedCheck
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

private fun history() = MaintenanceHistory(GearKind.Wing)

class MaintenanceModelTest {
    @Nested
    inner class IntervalCreation {
        @Test
        fun `accepts months only`() {
            assertThatCode { MaintenanceInterval(months = 12) }.doesNotThrowAnyException()
        }

        @Test
        fun `accepts hours only`() {
            assertThatCode { MaintenanceInterval(hours = 100.hours) }.doesNotThrowAnyException()
        }

        @Test
        fun `accepts months and hours together`() {
            assertThatCode { MaintenanceInterval(months = 24, hours = 100.hours) }.doesNotThrowAnyException()
        }

        @Test
        fun `rejects an interval without any limit`() {
            assertThatThrownBy { MaintenanceInterval() }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `rejects zero months`() {
            assertThatThrownBy { MaintenanceInterval(months = 0) }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `rejects zero hours`() {
            assertThatThrownBy { MaintenanceInterval(hours = Duration.ZERO) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class CheckCreation {
        @Test
        fun `a full check restarts the interval by default`() {
            assertThat(aCheck(type = CheckType.Full).restartsInterval).isTrue()
        }

        @Test
        fun `a partial check restarts the interval by default`() {
            assertThat(aCheck(type = CheckType.Partial).restartsInterval).isTrue()
        }

        @Test
        fun `an other check does not restart the interval by default`() {
            assertThat(aCheck(type = CheckType.Other).restartsInterval).isFalse()
        }

        @Test
        fun `an other check can be marked as restarting the interval`() {
            assertThat(aCheck(type = CheckType.Other, restartsInterval = true).restartsInterval).isTrue()
        }

        @Test
        fun `a full check cannot opt out of restarting the interval`() {
            assertThatThrownBy { aCheck(type = CheckType.Full, restartsInterval = false) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class HistoryChecks {
        @Test
        fun `keeps the history newest first`() {
            // given
            val wing = history()

            // when
            val updated =
                wing
                    .withCheck(aCheck("c-1", LocalDate(2024, 4, 1)))
                    .withCheck(aCheck("c-3", LocalDate(2026, 4, 1)))
                    .withCheck(aCheck("c-2", LocalDate(2025, 4, 1)))

            // then
            assertThat(updated.checks.map { it.id.value }).containsExactly("c-3", "c-2", "c-1")
        }

        @Test
        fun `rejects a second check with the same id`() {
            // given
            val wing = history().withCheck(aCheck("c-1"))

            // when / then
            assertThatThrownBy { wing.withCheck(aCheck("c-1")) }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `replaces an existing check and re-sorts`() {
            // given
            val wing =
                history()
                    .withCheck(
                        aCheck("c-1", LocalDate(2024, 4, 1)),
                    ).withCheck(aCheck("c-2", LocalDate(2025, 4, 1)))

            // when
            val updated = wing.withUpdatedCheck(aCheck("c-1", LocalDate(2026, 1, 1)))

            // then
            assertThat(updated.checks.map { it.id.value }).containsExactly("c-1", "c-2")
        }

        @Test
        fun `removes a check`() {
            // given
            val wing = history().withCheck(aCheck("c-1"))

            // when
            val updated = wing.withoutCheck(CheckId("c-1"))

            // then
            assertThat(updated.checks).isEmpty()
        }

        @Test
        fun `fails to remove a check that does not exist`() {
            assertThatThrownBy { history().withoutCheck(CheckId("missing")) }
                .isInstanceOf(NoSuchElementException::class.java)
        }

        @Test
        fun `the last periodic check ignores other checks that do not restart the interval`() {
            // given
            val wing =
                history()
                    .withCheck(aCheck("c-1", LocalDate(2025, 4, 1), CheckType.Full))
                    .withCheck(aCheck("c-2", LocalDate(2026, 1, 1), CheckType.Other))

            // when
            val last = wing.lastPeriodicCheck

            // then
            assertThat(last?.id?.value).isEqualTo("c-1")
        }

        @Test
        fun `the last periodic check includes an other check that restarts the interval`() {
            // given
            val wing =
                history()
                    .withCheck(aCheck("c-1", LocalDate(2025, 4, 1), CheckType.Full))
                    .withCheck(aCheck("c-2", LocalDate(2026, 1, 1), CheckType.Other, restartsInterval = true))

            // when
            val last = wing.lastPeriodicCheck

            // then
            assertThat(last?.id?.value).isEqualTo("c-2")
        }

        @Test
        fun `has no last periodic check without history`() {
            assertThat(history().lastPeriodicCheck).isNull()
        }
    }

    @Nested
    inner class AllowedTypes {
        @Test
        fun `a wing accepts partial, full and other checks`() {
            assertThatCode {
                MaintenanceHistory(
                    GearKind.Wing,
                    listOf(
                        aCheck("a", type = CheckType.Partial),
                        aCheck("b", type = CheckType.Full),
                        aCheck("c", type = CheckType.Other),
                    ),
                )
            }.doesNotThrowAnyException()
        }

        @Test
        fun `a wing does not accept a repack`() {
            assertThatThrownBy { MaintenanceHistory(GearKind.Wing, listOf(aCheck(type = CheckType.Repack))) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `a harness accepts a check but not a full check`() {
            assertThatCode { MaintenanceHistory(GearKind.Harness, listOf(aCheck(type = CheckType.Check))) }
                .doesNotThrowAnyException()
            assertThatThrownBy { MaintenanceHistory(GearKind.Harness, listOf(aCheck(type = CheckType.Full))) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `a reserve accepts a repack but not a partial check`() {
            assertThatCode { MaintenanceHistory(GearKind.Reserve, listOf(aCheck(type = CheckType.Repack))) }
                .doesNotThrowAnyException()
            assertThatThrownBy { MaintenanceHistory(GearKind.Reserve, listOf(aCheck(type = CheckType.Partial))) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `planned checks are held to the same types`() {
            assertThatThrownBy {
                MaintenanceHistory(GearKind.Reserve, plannedChecks = listOf(aPlannedCheck(type = CheckType.Full)))
            }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `only wings count flight hours`() {
            assertThat(GearKind.entries.filter { it.countsFlightHours }).containsExactly(GearKind.Wing)
        }
    }

    @Nested
    inner class HistoryPlannedChecks {
        @Test
        fun `the next planned check is the earliest one`() {
            // given
            val wing =
                history()
                    .withPlannedCheck(aPlannedCheck("p-1", LocalDate(2027, 3, 1)))
                    .withPlannedCheck(aPlannedCheck("p-2", LocalDate(2026, 12, 1)))

            // when
            val next = wing.nextPlannedCheck

            // then
            assertThat(next?.id?.value).isEqualTo("p-2")
        }

        @Test
        fun `cancels a planned check`() {
            // given
            val wing = history().withPlannedCheck(aPlannedCheck("p-1"))

            // when
            val updated = wing.withoutPlannedCheck(PlannedCheckId("p-1"))

            // then
            assertThat(updated.plannedChecks).isEmpty()
        }

        @Test
        fun `fails to update a planned check that does not exist`() {
            assertThatThrownBy { history().withUpdatedPlannedCheck(aPlannedCheck("missing")) }
                .isInstanceOf(NoSuchElementException::class.java)
        }
    }
}
