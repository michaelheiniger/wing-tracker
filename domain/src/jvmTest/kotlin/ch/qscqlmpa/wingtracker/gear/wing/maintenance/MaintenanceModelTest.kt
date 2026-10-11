package ch.qscqlmpa.wingtracker.gear.wing.maintenance

import ch.qscqlmpa.wingtracker.gear.wing.aCheck
import ch.qscqlmpa.wingtracker.gear.wing.aPlannedCheck
import ch.qscqlmpa.wingtracker.gear.wing.aWing
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

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
            assertThat(aCheck(type = WingCheckType.Full).restartsInterval).isTrue()
        }

        @Test
        fun `a partial check restarts the interval by default`() {
            assertThat(aCheck(type = WingCheckType.Partial).restartsInterval).isTrue()
        }

        @Test
        fun `an other check does not restart the interval by default`() {
            assertThat(aCheck(type = WingCheckType.Other).restartsInterval).isFalse()
        }

        @Test
        fun `an other check can be marked as restarting the interval`() {
            assertThat(aCheck(type = WingCheckType.Other, restartsInterval = true).restartsInterval).isTrue()
        }

        @Test
        fun `a full check cannot opt out of restarting the interval`() {
            assertThatThrownBy { aCheck(type = WingCheckType.Full, restartsInterval = false) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class WingChecks {
        @Test
        fun `keeps the history newest first`() {
            // given
            val wing = aWing()

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
            val wing = aWing().withCheck(aCheck("c-1"))

            // when / then
            assertThatThrownBy { wing.withCheck(aCheck("c-1")) }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `replaces an existing check and re-sorts`() {
            // given
            val wing =
                aWing()
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
            val wing = aWing().withCheck(aCheck("c-1"))

            // when
            val updated = wing.withoutCheck(CheckId("c-1"))

            // then
            assertThat(updated.checks).isEmpty()
        }

        @Test
        fun `fails to remove a check that does not exist`() {
            assertThatThrownBy { aWing().withoutCheck(CheckId("missing")) }
                .isInstanceOf(NoSuchElementException::class.java)
        }

        @Test
        fun `the last periodic check ignores other checks that do not restart the interval`() {
            // given
            val wing =
                aWing()
                    .withCheck(aCheck("c-1", LocalDate(2025, 4, 1), WingCheckType.Full))
                    .withCheck(aCheck("c-2", LocalDate(2026, 1, 1), WingCheckType.Other))

            // when
            val last = wing.lastPeriodicCheck

            // then
            assertThat(last?.id?.value).isEqualTo("c-1")
        }

        @Test
        fun `the last periodic check includes an other check that restarts the interval`() {
            // given
            val wing =
                aWing()
                    .withCheck(aCheck("c-1", LocalDate(2025, 4, 1), WingCheckType.Full))
                    .withCheck(aCheck("c-2", LocalDate(2026, 1, 1), WingCheckType.Other, restartsInterval = true))

            // when
            val last = wing.lastPeriodicCheck

            // then
            assertThat(last?.id?.value).isEqualTo("c-2")
        }

        @Test
        fun `has no last periodic check without history`() {
            assertThat(aWing().lastPeriodicCheck).isNull()
        }
    }

    @Nested
    inner class WingPlannedChecks {
        @Test
        fun `the next planned check is the earliest one`() {
            // given
            val wing =
                aWing()
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
            val wing = aWing().withPlannedCheck(aPlannedCheck("p-1"))

            // when
            val updated = wing.withoutPlannedCheck(PlannedCheckId("p-1"))

            // then
            assertThat(updated.plannedChecks).isEmpty()
        }

        @Test
        fun `fails to update a planned check that does not exist`() {
            assertThatThrownBy { aWing().withUpdatedPlannedCheck(aPlannedCheck("missing")) }
                .isInstanceOf(NoSuchElementException::class.java)
        }
    }
}
