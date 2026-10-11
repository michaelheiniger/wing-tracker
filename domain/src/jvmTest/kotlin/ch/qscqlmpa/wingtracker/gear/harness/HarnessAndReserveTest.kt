package ch.qscqlmpa.wingtracker.gear.harness

import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.aHarness
import ch.qscqlmpa.wingtracker.gear.aHarnessDetails
import ch.qscqlmpa.wingtracker.gear.aReserve
import ch.qscqlmpa.wingtracker.gear.aReserveDetails
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

class HarnessAndReserveTest {
    @Nested
    inner class HarnessCreation {
        @Test
        fun `has a reference of its own kind`() {
            assertThat(aHarness().ref).isEqualTo(GearRef(GearKind.Harness, "harness-1"))
        }

        @Test
        fun `rejects a blank name`() {
            assertThatThrownBy { aHarnessDetails(name = " ") }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `rejects an interval counted in flight hours`() {
            assertThatThrownBy { aHarnessDetails(maintenanceInterval = MaintenanceInterval(hours = 100.hours)) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("flight hours")
        }

        @Test
        fun `rejects a retirement before it entered service`() {
            assertThatThrownBy { aHarness(status = GearStatus.Retired(LocalDate(2024, 2, 1))) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `rejects a wing check type in its history`() {
            val check = MaintenanceCheck(CheckId("c-1"), LocalDate(2025, 3, 1), CheckType.Full)
            assertThatThrownBy { aHarness(checks = listOf(check)) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class HarnessBehaviour {
        @Test
        fun `retires with a date and reason`() {
            // given
            val harness = aHarness()

            // when
            val retired = harness.retire(LocalDate(2026, 9, 1), "Replaced")

            // then
            assertThat(retired.status).isEqualTo(GearStatus.Retired(LocalDate(2026, 9, 1), "Replaced"))
        }

        @Test
        fun `fails to retire twice`() {
            // given
            val retired = aHarness().retire(LocalDate(2026, 9, 1))

            // when / then
            assertThatThrownBy { retired.retire(LocalDate(2026, 9, 2)) }.isInstanceOf(IllegalStateException::class.java)
        }

        @Test
        fun `keeps the checks when its history is replaced`() {
            // given
            val check = MaintenanceCheck(CheckId("c-1"), LocalDate(2025, 3, 1), CheckType.Check)
            val harness = aHarness()

            // when
            val updated = harness.withHistory(harness.history.withCheck(check))

            // then
            assertThat(updated.checks).containsExactly(check)
        }
    }

    @Nested
    inner class ReserveCreation {
        @Test
        fun `has a reference of its own kind`() {
            assertThat(aReserve().ref).isEqualTo(GearRef(GearKind.Reserve, "reserve-1"))
        }

        @Test
        fun `accepts a reserve without a manufacture date`() {
            assertThat(aReserveDetails(manufactureDate = null).manufactureDate).isNull()
        }

        @Test
        fun `rejects an interval counted in flight hours`() {
            assertThatThrownBy { aReserveDetails(maintenanceInterval = MaintenanceInterval(hours = 100.hours)) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `rejects a partial check in its history`() {
            val check = MaintenanceCheck(CheckId("c-1"), LocalDate(2025, 3, 1), CheckType.Partial)
            assertThatThrownBy { aReserve(checks = listOf(check)) }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `accepts a repack in its history`() {
            val check = MaintenanceCheck(CheckId("c-1"), LocalDate(2025, 3, 1), CheckType.Repack)
            assertThat(aReserve(checks = listOf(check)).checks).containsExactly(check)
        }
    }
}
