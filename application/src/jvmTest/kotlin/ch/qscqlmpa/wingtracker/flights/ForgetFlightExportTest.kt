package ch.qscqlmpa.wingtracker.flights

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val SENT = FlightExport("airtime", Instant.parse("2026-10-17T20:12:04Z"), "986")

class ForgetFlightExportTest {
    private val flights = mockk<FlightRepository>(relaxUnitFun = true)
    private val forget = ForgetFlightExport(flights)
    private val exported = aFlight().copy(exports = listOf(SENT))

    @Nested
    inner class Invoke {
        @Test
        fun `saves the flight without the record and returns it`() =
            runTest {
                // given
                coEvery { flights.findById(exported.id) } returns exported

                // when
                val forgotten = forget(exported.id, "airtime")

                // then
                assertThat(forgotten).isEqualTo(SENT)
                coVerify(exactly = 1) { flights.save(exported.copy(exports = emptyList())) }
            }

        @Test
        fun `changes nothing when the flight was not exported there`() =
            runTest {
                // given
                coEvery { flights.findById(exported.id) } returns aFlight()

                // when
                val forgotten = forget(exported.id, "airtime")

                // then
                assertThat(forgotten).isNull()
                coVerify(exactly = 0) { flights.save(any()) }
            }
    }

    @Nested
    inner class Restore {
        @Test
        fun `puts the record back on the flight as it is now`() =
            runTest {
                // given
                val edited = aFlight(details = aFlightDetails(duration = 100.minutes))
                coEvery { flights.findById(exported.id) } returns edited

                // when
                forget.restore(exported.id, SENT)

                // then
                coVerify(exactly = 1) { flights.save(edited.copy(exports = listOf(SENT))) }
            }
    }
}
