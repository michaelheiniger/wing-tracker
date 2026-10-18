package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private fun aFlightDetails(duration: Duration = 95.minutes) =
    FlightDetails(
        date = LocalDate(2026, 9, 20),
        duration = duration,
        wingId = WingId("3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47"),
        takeOffSite = SiteNames.of(SiteSource.Manual, "Niederhorn"),
        landingSite = SiteNames.of(SiteSource.Manual, "Beatenbucht"),
        notes = "Thermic, smooth air",
    )

class FlightDetailsTest {
    @Nested
    inner class Creation {
        @Test
        fun `accepts a flight of at least one minute`() {
            assertThatCode { aFlightDetails(duration = 1.minutes) }.doesNotThrowAnyException()
        }

        @Test
        fun `rejects a zero duration`() {
            assertThatThrownBy { aFlightDetails(duration = Duration.ZERO) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `rejects a negative duration`() {
            assertThatThrownBy { aFlightDetails(duration = (-5).minutes) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
