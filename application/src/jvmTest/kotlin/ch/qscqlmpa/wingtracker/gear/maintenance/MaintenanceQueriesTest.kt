package ch.qscqlmpa.wingtracker.gear.maintenance

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.aFixedClock
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.aWing
import ch.qscqlmpa.wingtracker.gear.wing.aWingDetails
import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
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
    private val gear = mockk<MaintainedGearRepository>()
    private val flights = mockk<FlightRepository>()
    private val settings = mockk<SettingsRepository>().also { coEvery { it.load() } returns AppSettings() }
    private val interval = MaintenanceInterval(months = 12, hours = 100.hours)
    private val skyDancer =
        aWing(
            id = "wing-1",
            details = aWingDetails(hoursBeforeTracking = 42.hours, maintenanceInterval = interval),
            checks = listOf(aStoredCheck(date = LocalDate(2025, 4, 10))),
        )
    private val cloudSurfer = aWing(id = "wing-2", details = aWingDetails(name = "Cloud Surfer"))
    private val harness = aHarnessWithInterval()

    private fun givenStored(
        vararg flightList: Flight,
        all: List<MaintainedGear> = listOf(skyDancer, cloudSurfer),
    ) {
        coEvery { gear.findAll() } returns all
        coEvery { gear.find(skyDancer.ref) } returns skyDancer
        coEvery { gear.find(harness.ref) } returns harness
        coEvery { flights.findAll() } returns LoadResult(flightList.toList())
    }

    @Nested
    inner class GearMaintenanceQuery {
        @Test
        fun `reports the status and the total hours of a wing`() =
            runTest {
                // given
                givenStored(
                    aFlightOn("wing-1", LocalDate(2025, 1, 1), 10),
                    aFlightOn("wing-1", LocalDate(2025, 6, 1), 20),
                    aFlightOn("wing-2", LocalDate(2025, 6, 1), 99),
                )

                // when
                val result = GetGearMaintenance(gear, flights, settings, aFixedClock())(skyDancer.ref)

                // then
                assertThat(result.totalHours).isEqualTo(72.hours)
                assertThat(result.status?.hoursSince).isEqualTo(20.hours)
                assertThat(result.status?.dueDate).isEqualTo(LocalDate(2026, 4, 10))
                assertThat(result.status?.isDue).isTrue()
            }

        @Test
        fun `reports a harness without total hours, due by months only`() =
            runTest {
                // given
                givenStored(aFlightOn("wing-1", LocalDate(2025, 6, 1), 20), all = listOf(skyDancer, harness))

                // when
                val result = GetGearMaintenance(gear, flights, settings, aFixedClock())(harness.ref)

                // then
                assertThat(result.totalHours).isNull()
                assertThat(result.status?.hoursSince).isEqualTo(kotlin.time.Duration.ZERO)
                assertThat(result.status?.dueDate).isEqualTo(LocalDate(2026, 3, 1))
                assertThat(result.status?.isDue).isTrue()
            }

        @Test
        fun `suggests the companies already used by any gear, most used first`() =
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
                givenStored(all = listOf(skyDancer, many, harness))

                // when
                val result = GetGearMaintenance(gear, flights, settings, aFixedClock())(skyDancer.ref)

                // then
                assertThat(result.knownCompanies).containsExactly("Alpine Wing Service", "Swiss Paragliding Service")
            }

        @Test
        fun `fails when the gear does not exist`() =
            runTest {
                // given
                val ref = GearRef(GearKind.Reserve, "missing")
                coEvery { gear.find(ref) } returns null

                // when
                val failure =
                    runCatching {
                        GetGearMaintenance(
                            gear,
                            flights,
                            settings,
                            aFixedClock(),
                        )(ref)
                    }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(GearNotFoundException::class.java)
            }
    }

    @Nested
    inner class StatusesQuery {
        @Test
        fun `reports a status per piece of gear and null for gear without a rule`() =
            runTest {
                // given
                givenStored(
                    aFlightOn("wing-1", LocalDate(2025, 6, 1), 20),
                    all = listOf(skyDancer, cloudSurfer, harness),
                )

                // when
                val result = GetMaintenanceStatuses(gear, flights, settings, aFixedClock())()

                // then
                assertThat(result[skyDancer.ref]?.hoursSince).isEqualTo(20.hours)
                assertThat(result[cloudSurfer.ref]).isNull()
                assertThat(result[harness.ref]?.dueDate).isEqualTo(LocalDate(2026, 3, 1))
            }
    }
}
