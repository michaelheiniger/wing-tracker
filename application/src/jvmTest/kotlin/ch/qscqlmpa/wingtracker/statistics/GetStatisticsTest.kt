package ch.qscqlmpa.wingtracker.statistics

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightFilter
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckId
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckType
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Own factories: the statistics tests do not depend on other modules' helpers.
private val TODAY = LocalDate(2026, 10, 11)

private fun aWing(
    id: String,
    name: String = "Sky Dancer",
    inService: LocalDate = LocalDate(2024, 3, 9),
    before: Duration = 42.hours,
    flightsBefore: Int = 61,
    status: GearStatus = GearStatus.Active,
    checks: List<MaintenanceCheck> = emptyList(),
) = Wing(
    id = WingId(id),
    details =
        WingDetails(
            name = name,
            manufacturer = "Advance",
            model = "Iota 3",
            acquisition = Acquisition(AcquisitionCondition.New),
            inServiceDate = inService,
            hoursBeforeTracking = before,
            flightsBeforeTracking = flightsBefore,
        ),
    status = status,
    checks = checks,
)

private fun aCheck(
    date: LocalDate,
    type: CheckType = CheckType.Full,
) = MaintenanceCheck(id = CheckId("check-$date"), date = date, type = type, company = "Swiss Paragliding Service")

private var flightCounter = 0

private fun aFlight(
    wingId: String,
    date: LocalDate,
    duration: Duration = 60.minutes,
) = Flight(
    id = FlightId("flight-${flightCounter++}"),
    details = FlightDetails(date = date, duration = duration, wingId = WingId(wingId)),
    loggedAt = Instant.parse("2026-10-01T10:00:00Z"),
)

class GetStatisticsTest {
    private val flights = mockk<FlightRepository>()
    private val wings = mockk<WingRepository>()
    private val clock =
        AppClock(
            clock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("2026-10-11T18:30:00Z")
                },
            zone = TimeZone.UTC,
        )

    private fun givenStored(
        wingList: List<Wing>,
        vararg flightList: Flight,
    ) {
        coEvery { wings.findAll() } returns LoadResult(wingList)
        coEvery { flights.findAll() } returns LoadResult(flightList.toList())
    }

    private suspend fun statistics(filter: FlightFilter = FlightFilter()) = GetStatistics(flights, wings, clock)(filter)

    @Nested
    inner class Yearly {
        @Test
        fun `sums tracked flight hours per year and per wing`() =
            runTest {
                // given
                val sky = aWing("wing-1")
                val cloud = aWing("wing-2", name = "Cloud Surfer")
                givenStored(
                    listOf(sky, cloud),
                    aFlight("wing-1", LocalDate(2025, 5, 1), 90.minutes),
                    aFlight("wing-1", LocalDate(2025, 8, 1), 30.minutes),
                    aFlight("wing-2", LocalDate(2025, 8, 1), 45.minutes),
                    aFlight("wing-1", LocalDate(2026, 4, 1), 60.minutes),
                )

                // when
                val result = statistics()

                // then
                assertThat(result.yearly.map { it.year }).containsExactly(2025, 2026)
                assertThat(result.yearly[0].hours[SeriesKey.OfWing(sky.id)]).isEqualTo(120.minutes)
                assertThat(result.yearly[0].hours[SeriesKey.OfWing(cloud.id)]).isEqualTo(45.minutes)
                assertThat(result.yearly[1].hours[SeriesKey.OfWing(sky.id)]).isEqualTo(60.minutes)
            }

        @Test
        fun `keeps years without flights so the time axis stays honest`() =
            runTest {
                // given
                givenStored(
                    listOf(aWing("wing-1")),
                    aFlight("wing-1", LocalDate(2024, 6, 1)),
                    aFlight("wing-1", LocalDate(2026, 6, 1)),
                )

                // when
                val result = statistics()

                // then
                assertThat(result.yearly.map { it.year }).containsExactly(2024, 2025, 2026)
                assertThat(result.yearly[1].hours).isEmpty()
            }

        @Test
        fun `is empty when nothing was flown`() =
            runTest {
                // given
                givenStored(listOf(aWing("wing-1")))

                // when
                val result = statistics()

                // then
                assertThat(result.isEmpty).isTrue()
            }

        @Test
        fun `ignores flights of a wing that does not load`() =
            runTest {
                // given
                givenStored(listOf(aWing("wing-1")), aFlight("missing-wing", LocalDate(2025, 5, 1)))

                // when
                val result = statistics()

                // then
                assertThat(result.isEmpty).isTrue()
            }
    }

    @Nested
    inner class Cumulative {
        @Test
        fun `starts at the hours before tracking on the in-service date and grows with each flight day`() =
            runTest {
                // given
                val sky = aWing("wing-1", inService = LocalDate(2024, 3, 9), before = 42.hours)
                givenStored(
                    listOf(sky),
                    aFlight("wing-1", LocalDate(2025, 5, 1), 90.minutes),
                    aFlight("wing-1", LocalDate(2025, 5, 1), 30.minutes),
                    aFlight("wing-1", LocalDate(2026, 4, 1), 60.minutes),
                )

                // when
                val points = statistics().cumulative.getValue(SeriesKey.OfWing(sky.id))

                // then
                assertThat(points.map { it.date to it.total }).containsExactly(
                    LocalDate(2024, 3, 9) to 42.hours,
                    LocalDate(2025, 5, 1) to 44.hours,
                    LocalDate(2026, 4, 1) to 45.hours,
                    TODAY to 45.hours,
                )
            }

        @Test
        fun `merges a flight on the in-service day into the starting point`() =
            runTest {
                // given
                val sky = aWing("wing-1", inService = LocalDate(2024, 3, 9), before = 0.hours)
                givenStored(listOf(sky), aFlight("wing-1", LocalDate(2024, 3, 9), 60.minutes))

                // when
                val points = statistics().cumulative.getValue(SeriesKey.OfWing(sky.id))

                // then
                assertThat(points.first()).isEqualTo(CumulativePoint(LocalDate(2024, 3, 9), 60.minutes))
                assertThat(points.map { it.date }).doesNotHaveDuplicates()
            }

        @Test
        fun `starts on the first flight when it predates the in-service date`() =
            runTest {
                // given
                val sky = aWing("wing-1", inService = LocalDate(2024, 3, 9), before = 42.hours)
                givenStored(
                    listOf(sky),
                    aFlight("wing-1", LocalDate(2024, 3, 1), 60.minutes),
                    aFlight("wing-1", LocalDate(2024, 4, 1), 30.minutes),
                )

                // when
                val points = statistics().cumulative.getValue(SeriesKey.OfWing(sky.id))

                // then
                assertThat(points.map { it.date to it.total }).containsExactly(
                    LocalDate(2024, 3, 1) to 43.hours,
                    LocalDate(2024, 4, 1) to 43.hours + 30.minutes,
                    TODAY to 43.hours + 30.minutes,
                )
            }

        @Test
        fun `ends a retired wing on its retirement date`() =
            runTest {
                // given
                val retired = aWing("wing-1", status = GearStatus.Retired(LocalDate(2025, 9, 30)))
                givenStored(listOf(retired), aFlight("wing-1", LocalDate(2025, 5, 1)))

                // when
                val points = statistics().cumulative.getValue(SeriesKey.OfWing(retired.id))

                // then
                assertThat(points.last().date).isEqualTo(LocalDate(2025, 9, 30))
            }

        @Test
        fun `looks up the hours on any date`() =
            runTest {
                // given
                val sky = aWing("wing-1", inService = LocalDate(2024, 3, 9), before = 42.hours)
                givenStored(listOf(sky), aFlight("wing-1", LocalDate(2025, 5, 1), 90.minutes))
                val key = SeriesKey.OfWing(sky.id)

                // when
                val result = statistics()

                // then
                assertThat(result.cumulativeAt(key, LocalDate(2024, 1, 1))).isEqualTo(Duration.ZERO)
                assertThat(result.cumulativeAt(key, LocalDate(2025, 4, 30))).isEqualTo(42.hours)
                assertThat(result.cumulativeAt(key, LocalDate(2025, 5, 1))).isEqualTo(43.hours + 30.minutes)
            }
    }

    @Nested
    inner class YearlyFlights {
        @Test
        fun `counts tracked flights per year and per wing`() =
            runTest {
                // given
                val sky = aWing("wing-1")
                val cloud = aWing("wing-2", name = "Cloud Surfer")
                givenStored(
                    listOf(sky, cloud),
                    aFlight("wing-1", LocalDate(2025, 5, 1)),
                    aFlight("wing-1", LocalDate(2025, 8, 1)),
                    aFlight("wing-2", LocalDate(2025, 8, 1)),
                    aFlight("wing-1", LocalDate(2026, 4, 1)),
                )

                // when
                val result = statistics()

                // then
                assertThat(result.yearlyFlights.map { it.year to it.counts }).containsExactly(
                    2025 to mapOf(SeriesKey.OfWing(sky.id) to 2, SeriesKey.OfWing(cloud.id) to 1),
                    2026 to mapOf(SeriesKey.OfWing(sky.id) to 1),
                )
            }

        @Test
        fun `keeps years without flights so the time axis stays honest`() =
            runTest {
                // given
                givenStored(
                    listOf(aWing("wing-1")),
                    aFlight("wing-1", LocalDate(2024, 6, 1)),
                    aFlight("wing-1", LocalDate(2026, 6, 1)),
                )

                // when
                val result = statistics()

                // then
                assertThat(result.yearlyFlights.map { it.year }).containsExactly(2024, 2025, 2026)
                assertThat(result.yearlyFlights[1].counts).isEmpty()
            }
    }

    @Nested
    inner class CumulativeFlights {
        @Test
        fun `starts at the flights before tracking and grows with each flight day`() =
            runTest {
                // given
                val sky = aWing("wing-1", inService = LocalDate(2024, 3, 9), flightsBefore = 61)
                givenStored(
                    listOf(sky),
                    aFlight("wing-1", LocalDate(2025, 5, 1)),
                    aFlight("wing-1", LocalDate(2025, 5, 1)),
                    aFlight("wing-1", LocalDate(2026, 4, 1)),
                )

                // when
                val points = statistics().cumulativeFlights.getValue(SeriesKey.OfWing(sky.id))

                // then
                assertThat(points.map { it.date to it.total }).containsExactly(
                    LocalDate(2024, 3, 9) to 61,
                    LocalDate(2025, 5, 1) to 63,
                    LocalDate(2026, 4, 1) to 64,
                    TODAY to 64,
                )
            }

        @Test
        fun `sums the folded wings into the other series`() =
            runTest {
                // given
                val many =
                    (1..10).map {
                        aWing(
                            "wing-$it",
                            name = "Wing $it",
                            inService = LocalDate(2020 + it / 5, it, 1),
                            flightsBefore = 5,
                        )
                    }
                givenStored(many, aFlight("wing-9", LocalDate(2026, 1, 5)), aFlight("wing-10", LocalDate(2026, 2, 5)))

                // when
                val result = statistics()

                // then
                assertThat(result.yearlyFlights.single().counts[SeriesKey.Other]).isEqualTo(2)
                assertThat(result.cumulativeFlightsAt(SeriesKey.Other, TODAY)).isEqualTo(12)
            }
    }

    @Nested
    inner class Totals {
        @Test
        fun `adds every wing up per year, gap years included`() =
            runTest {
                // given
                givenStored(
                    listOf(aWing("w-1"), aWing("w-2", name = "Birdy")),
                    aFlight("w-1", LocalDate(2024, 5, 1), 90.minutes),
                    aFlight("w-2", LocalDate(2024, 6, 1), 20.minutes),
                    aFlight("w-2", LocalDate(2026, 7, 1), 15.minutes),
                )

                // when
                val totals = statistics().totals

                // then
                assertThat(totals.years).containsExactly(
                    YearTotal(2024, 2, 110.minutes),
                    YearTotal(2025, 0, Duration.ZERO),
                    YearTotal(2026, 1, 15.minutes),
                )
            }

        @Test
        fun `counts what was flown before tracking in the overall total but in no year`() =
            runTest {
                // given
                givenStored(
                    listOf(
                        aWing("w-1", before = 42.hours, flightsBefore = 61),
                        aWing("w-2", before = 3.hours, flightsBefore = 4),
                    ),
                    aFlight("w-1", LocalDate(2026, 5, 1), 90.minutes),
                )

                // when
                val totals = statistics().totals

                // then
                assertThat(totals.flightsBeforeTracking).isEqualTo(65)
                assertThat(totals.hoursBeforeTracking).isEqualTo(45.hours)
                assertThat(totals.flights).isEqualTo(66)
                assertThat(totals.hours).isEqualTo(46.hours + 30.minutes)
            }

        @Test
        fun `includes retired wings and wings folded into the other series`() =
            runTest {
                // given
                val many = (1..MAX_NAMED_SERIES + 1).map { aWing("w-$it", before = Duration.ZERO, flightsBefore = 0) }
                val retired =
                    aWing(
                        "w-retired",
                        before = Duration.ZERO,
                        flightsBefore = 0,
                        status = GearStatus.Retired(LocalDate(2025, 1, 1)),
                    )
                givenStored(
                    many + retired,
                    *(many + retired).map { aFlight(it.id.value, LocalDate(2024, 8, 1)) }.toTypedArray(),
                )

                // when / then
                assertThat(statistics().totals.flights).isEqualTo(MAX_NAMED_SERIES + 2)
            }
    }

    @Nested
    inner class SinceLastCheck {
        @Test
        fun `counts flights and hours from the day of the latest check of any type`() =
            runTest {
                // given
                val sky =
                    aWing(
                        "wing-1",
                        checks = listOf(aCheck(LocalDate(2026, 6, 1), CheckType.Other), aCheck(LocalDate(2025, 6, 1))),
                    )
                givenStored(
                    listOf(sky),
                    aFlight("wing-1", LocalDate(2026, 5, 31), 60.minutes),
                    aFlight("wing-1", LocalDate(2026, 6, 1), 90.minutes),
                    aFlight("wing-1", LocalDate(2026, 8, 15), 45.minutes),
                )

                // when
                val since = statistics().sinceLastCheck.single()

                // then
                assertThat(since.check?.date).isEqualTo(LocalDate(2026, 6, 1))
                assertThat(since.flights).isEqualTo(2)
                assertThat(since.hours).isEqualTo(135.minutes)
            }

        @Test
        fun `reports no check and nothing counted for a wing never checked`() =
            runTest {
                // given
                givenStored(listOf(aWing("wing-1")), aFlight("wing-1", LocalDate(2026, 5, 31)))

                // when
                val since = statistics().sinceLastCheck.single()

                // then
                assertThat(since.check).isNull()
                assertThat(since.flights).isZero()
            }

        @Test
        fun `leaves out retired wings`() =
            runTest {
                // given
                val retired = aWing("wing-1", status = GearStatus.Retired(LocalDate(2025, 9, 30)))
                givenStored(listOf(retired, aWing("wing-2", name = "Cloud Surfer")))

                // when
                val result = statistics()

                // then
                assertThat(result.sinceLastCheck.map { it.wing.id }).containsExactly(WingId("wing-2"))
            }
    }

    @Nested
    inner class Searched {
        private val sky =
            aWing("wing-1", before = 42.hours, flightsBefore = 61, checks = listOf(aCheck(LocalDate(2025, 3, 1))))
        private val cloud = aWing("wing-2", name = "Cloud Surfer", inService = LocalDate(2025, 1, 1))
        private val summer2025 = FlightFilter(from = LocalDate(2025, 6, 1), to = LocalDate(2025, 8, 31))

        private fun givenFlown() =
            givenStored(
                listOf(sky, cloud),
                aFlight("wing-1", LocalDate(2025, 5, 20), 30.minutes),
                aFlight("wing-1", LocalDate(2025, 7, 4), 90.minutes),
                aFlight("wing-1", LocalDate(2025, 8, 15), 60.minutes),
                aFlight("wing-2", LocalDate(2026, 4, 2), 45.minutes),
            )

        @Test
        fun `counts only the matching flights, without those before tracking`() =
            runTest {
                // given
                givenFlown()

                // when
                val result = statistics(summer2025)

                // then
                assertThat(result.totals.flights).isEqualTo(2)
                assertThat(result.totals.hours).isEqualTo(150.minutes)
                assertThat(result.totals.flightsBeforeTracking).isZero()
                assertThat(result.yearly.map { it.year }).containsExactly(2025)
            }

        @Test
        fun `draws a wing from zero at the start of the search to its end`() =
            runTest {
                // given
                givenFlown()

                // when
                val result = statistics(summer2025)

                // then
                assertThat(result.cumulative[SeriesKey.OfWing(sky.id)]).containsExactly(
                    CumulativePoint(LocalDate(2025, 6, 1), Duration.ZERO),
                    CumulativePoint(LocalDate(2025, 7, 4), 90.minutes),
                    CumulativePoint(LocalDate(2025, 8, 15), 150.minutes),
                    CumulativePoint(LocalDate(2025, 8, 31), 150.minutes),
                )
                assertThat(result.until).isEqualTo(LocalDate(2025, 8, 31))
            }

        @Test
        fun `starts a line on the first matching flight when the search has no start`() =
            runTest {
                // given
                givenFlown()

                // when
                val result = statistics(FlightFilter(wingIds = setOf(cloud.id)))

                // then
                assertThat(result.cumulative[SeriesKey.OfWing(cloud.id)]?.first())
                    .isEqualTo(CumulativePoint(LocalDate(2026, 4, 2), 45.minutes))
                assertThat(result.until).isEqualTo(TODAY)
            }

        @Test
        fun `shows only the wings it found flights for, keeping every series for stable colors`() =
            runTest {
                // given
                givenFlown()

                // when
                val result = statistics(summer2025)

                // then
                assertThat(result.shownSeries.map { it.key }).containsExactly(SeriesKey.OfWing(sky.id))
                assertThat(result.series).hasSize(2)
                assertThat(result.cumulative[SeriesKey.OfWing(cloud.id)]).isEmpty()
            }

        @Test
        fun `leaves out what was flown since the last check`() =
            runTest {
                // given
                givenFlown()

                // when
                val result = statistics(summer2025)

                // then
                assertThat(result.sinceLastCheck).isEmpty()
            }

        @Test
        fun `is empty when no flight matches`() =
            runTest {
                // given
                givenFlown()

                // when
                val result = statistics(FlightFilter(text = "fiesch"))

                // then
                assertThat(result.isEmpty).isTrue()
            }
    }

    @Nested
    inner class Series {
        @Test
        fun `orders series by when the wings entered service`() =
            runTest {
                // given
                val later = aWing("wing-1", name = "Aardvark", inService = LocalDate(2025, 1, 1))
                val earlier = aWing("wing-2", name = "Zebra", inService = LocalDate(2023, 1, 1))
                givenStored(listOf(later, earlier), aFlight("wing-1", LocalDate(2025, 5, 1)))

                // when
                val result = statistics()

                // then
                assertThat(result.series.map { it.key }).containsExactly(
                    SeriesKey.OfWing(earlier.id),
                    SeriesKey.OfWing(later.id),
                )
            }

        @Test
        fun `folds wings beyond the color limit into one other series`() =
            runTest {
                // given
                val many =
                    (1..10).map {
                        aWing(
                            "wing-$it",
                            name = "Wing $it",
                            inService = LocalDate(2020 + it / 5, it, 1),
                            before = 10.hours,
                        )
                    }
                givenStored(
                    many,
                    aFlight("wing-9", LocalDate(2026, 1, 5), 60.minutes),
                    aFlight("wing-10", LocalDate(2026, 2, 5), 30.minutes),
                )

                // when
                val result = statistics()

                // then
                assertThat(result.series).hasSize(MAX_NAMED_SERIES + 1)
                val other = result.series.last()
                assertThat(other.key).isEqualTo(SeriesKey.Other)
                assertThat(other.wings).hasSize(2)
                assertThat(result.yearly.single().hours[SeriesKey.Other]).isEqualTo(90.minutes)
                assertThat(result.cumulativeAt(SeriesKey.Other, TODAY)).isEqualTo(20.hours + 90.minutes)
            }
    }
}
