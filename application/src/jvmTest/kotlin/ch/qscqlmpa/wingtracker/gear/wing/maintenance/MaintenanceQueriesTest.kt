package ch.qscqlmpa.wingtracker.gear.wing.maintenance

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.aFixedClock
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.gear.wing.aWing
import ch.qscqlmpa.wingtracker.gear.wing.aWingDetails
import ch.qscqlmpa.wingtracker.shared.LoadResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private fun aFlightOn(
    wingId: String,
    date: LocalDate,
    hours: Int,
) = Flight(
    id = FlightId("f-$wingId-$date"),
    details = FlightDetails(date = date, duration = hours.hours, wingId = WingId(wingId)),
    loggedAt = Instant.parse("2026-10-01T10:00:00Z"),
)

class MaintenanceQueriesTest {
    private val wings = mockk<WingRepository>()
    private val flights = mockk<FlightRepository>()
    private val interval = MaintenanceInterval(months = 12, hours = 100.hours)
    private val skyDancer =
        aWing(
            id = "wing-1",
            details = aWingDetails(hoursBeforeTracking = 42.hours, maintenanceInterval = interval),
            checks = listOf(aStoredCheck(date = LocalDate(2025, 4, 10))),
        )
    private val cloudSurfer = aWing(id = "wing-2", details = aWingDetails(name = "Cloud Surfer"))

    private fun givenStored(vararg flightList: Flight) {
        coEvery { wings.findAll() } returns LoadResult(listOf(skyDancer, cloudSurfer))
        coEvery { wings.findById(skyDancer.id) } returns skyDancer
        coEvery { flights.findAll() } returns LoadResult(flightList.toList())
    }

    @Nested
    inner class WingMaintenanceQuery {
        @Test
        fun `reports the status and the total hours of the wing`() =
            runTest {
                // given
                givenStored(
                    aFlightOn("wing-1", LocalDate(2025, 1, 1), 10),
                    aFlightOn("wing-1", LocalDate(2025, 6, 1), 20),
                    aFlightOn("wing-2", LocalDate(2025, 6, 1), 99),
                )

                // when
                val result = GetWingMaintenance(wings, flights, aFixedClock())(skyDancer.id)

                // then
                assertThat(result.totalHours).isEqualTo(72.hours)
                assertThat(result.status?.hoursSince).isEqualTo(20.hours)
                assertThat(result.status?.dueDate).isEqualTo(LocalDate(2026, 4, 10))
                assertThat(result.status?.isDue).isTrue()
            }

        @Test
        fun `suggests the companies already used, most used first`() =
            runTest {
                // given
                val many =
                    aWing(
                        id = "wing-3",
                        checks =
                            listOf(
                                aStoredCheck("c-2").copy(company = "Alpine Wing Service"),
                                aStoredCheck("c-3").copy(company = "Alpine Wing Service"),
                            ),
                    )
                coEvery { wings.findAll() } returns LoadResult(listOf(skyDancer, many))
                coEvery { wings.findById(skyDancer.id) } returns skyDancer
                coEvery { flights.findAll() } returns LoadResult(emptyList())

                // when
                val result = GetWingMaintenance(wings, flights, aFixedClock())(skyDancer.id)

                // then
                assertThat(result.knownCompanies).containsExactly("Alpine Wing Service", "Swiss Paragliding Service")
            }
    }

    @Nested
    inner class StatusesQuery {
        @Test
        fun `reports a status per wing and null for a wing without a rule`() =
            runTest {
                // given
                givenStored(aFlightOn("wing-1", LocalDate(2025, 6, 1), 20))

                // when
                val result = GetMaintenanceStatuses(wings, flights, aFixedClock())()

                // then
                assertThat(result[skyDancer.id]?.hoursSince).isEqualTo(20.hours)
                assertThat(result).containsKey(cloudSurfer.id)
                assertThat(result[cloudSurfer.id]).isNull()
            }
    }
}
