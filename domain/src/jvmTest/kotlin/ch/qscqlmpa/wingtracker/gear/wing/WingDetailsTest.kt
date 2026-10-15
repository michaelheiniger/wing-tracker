package ch.qscqlmpa.wingtracker.gear.wing

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

class WingDetailsTest {
    @Nested
    inner class Creation {
        @Test
        fun `accepts a fully populated wing`() {
            // given / when / then
            assertThatCode { aWingDetails() }.doesNotThrowAnyException()
        }

        @Test
        fun `accepts a wing with only the mandatory attributes`() {
            // given / when / then
            assertThatCode {
                aWingDetails(size = null, serialNumber = null, certificationClass = null)
            }.doesNotThrowAnyException()
        }

        @Test
        fun `rejects a blank name`() {
            // given / when / then
            assertThatThrownBy { aWingDetails(name = "  ") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("name")
        }

        @Test
        fun `rejects a blank manufacturer`() {
            // given / when / then
            assertThatThrownBy { aWingDetails(manufacturer = "") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("manufacturer")
        }

        @Test
        fun `rejects a blank model`() {
            // given / when / then
            assertThatThrownBy { aWingDetails(model = "") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("model")
        }

        @Test
        fun `rejects negative hours before tracking`() {
            // given / when / then
            assertThatThrownBy { aWingDetails(hoursBeforeTracking = (-1).hours) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("negative")
        }

        @Test
        fun `rejects negative flights before tracking`() {
            // given / when / then
            assertThatThrownBy { aWingDetails(flightsBeforeTracking = -1) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("negative")
        }
    }
}
