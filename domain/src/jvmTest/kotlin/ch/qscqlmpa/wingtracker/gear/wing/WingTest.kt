package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.gear.GearStatus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class WingTest {
    @Nested
    inner class WasInServiceOn {
        private val inService = LocalDate(2023, 5, 1)

        @Test
        fun `is true on the in-service date and after it`() {
            // given
            val wing = aWing().let { it.withDetails(it.details.copy(inServiceDate = inService)) }

            // when / then
            assertThat(wing.wasInServiceOn(inService)).isTrue()
            assertThat(wing.wasInServiceOn(inService.plus(1, DateTimeUnit.DAY))).isTrue()
        }

        @Test
        fun `is false before the in-service date`() {
            // given
            val wing = aWing().let { it.withDetails(it.details.copy(inServiceDate = inService)) }

            // when / then
            assertThat(wing.wasInServiceOn(inService.minus(1, DateTimeUnit.DAY))).isFalse()
        }

        @Test
        fun `is always true without an in-service date`() {
            // given
            val wing = aWing().let { it.withDetails(it.details.copy(inServiceDate = null)) }

            // when / then
            assertThat(wing.wasInServiceOn(LocalDate(1990, 1, 1))).isTrue()
        }
    }

    @Nested
    inner class Retire {
        @Test
        fun `marks an active wing as retired with date and reason`() {
            // given
            val wing = aWing()

            // when
            val retired = wing.retire(on = LocalDate(2026, 9, 30), reason = "Sold")

            // then
            assertThat(retired.status).isEqualTo(GearStatus.Retired(LocalDate(2026, 9, 30), "Sold"))
            assertThat(retired.isActive).isFalse()
        }

        @Test
        fun `keeps the details and identity of the wing`() {
            // given
            val wing = aWing()

            // when
            val retired = wing.retire(on = LocalDate(2026, 9, 30), reason = null)

            // then
            assertThat(retired).usingRecursiveComparison().ignoringFields("status").isEqualTo(wing)
        }

        @Test
        fun `treats a blank reason as no reason`() {
            // given
            val wing = aWing()

            // when
            val retired = wing.retire(on = LocalDate(2026, 9, 30), reason = "   ")

            // then
            assertThat(retired.status).isEqualTo(GearStatus.Retired(LocalDate(2026, 9, 30), null))
        }

        @Test
        fun `fails when the wing is already retired`() {
            // given
            val wing = aWing(status = GearStatus.Retired(LocalDate(2026, 1, 10), "Lost"))

            // when / then
            assertThatThrownBy { wing.retire(on = LocalDate(2026, 9, 30), reason = null) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("already retired")
        }

        @Test
        fun `fails when retired before it entered service`() {
            // given
            val wing = aWing(details = aWingDetails(inServiceDate = LocalDate(2023, 5, 1)))

            // when / then
            assertThatThrownBy { wing.retire(on = LocalDate(2023, 4, 30), reason = null) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("before it entered service")
        }
    }

    @Nested
    inner class WithDetails {
        @Test
        fun `replaces the details and keeps identity and status`() {
            // given
            val wing = aWing()
            val newDetails = aWingDetails(name = "Cloud Surfer", size = "L")

            // when
            val updated = wing.withDetails(newDetails)

            // then
            assertThat(updated.details).isEqualTo(newDetails)
            assertThat(updated.id).isEqualTo(wing.id)
            assertThat(updated.status).isEqualTo(wing.status)
        }
    }
}
