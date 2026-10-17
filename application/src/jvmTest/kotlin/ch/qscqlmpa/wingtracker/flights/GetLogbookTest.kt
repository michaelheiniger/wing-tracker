package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class GetLogbookTest {
    private val flights = mockk<FlightRepository>()
    private val wings = mockk<WingRepository>()
    private val skyDancer = aWingInService(id = "wing-1", name = "Sky Dancer", hoursBeforeTracking = 42.hours)
    private val cloudSurfer = aWingInService(id = "wing-2", name = "Cloud Surfer", hoursBeforeTracking = 0.hours)

    private fun givenFlights(vararg list: Flight) {
        coEvery { flights.findAll() } returns LoadResult(list.toList())
        coEvery { wings.findAll() } returns LoadResult(listOf(skyDancer, cloudSurfer))
    }

    @Nested
    inner class Months {
        @Test
        fun `groups flights by month with the newest month and flight first`() =
            runTest {
                // given
                val september = aFlight("f-1", aFlightDetails(date = LocalDate(2026, 9, 20), duration = 60.minutes))
                val octoberEarly = aFlight("f-2", aFlightDetails(date = LocalDate(2026, 10, 2), duration = 30.minutes))
                val octoberLate = aFlight("f-3", aFlightDetails(date = LocalDate(2026, 10, 9), duration = 45.minutes))
                givenFlights(september, octoberEarly, octoberLate)

                // when
                val logbook = GetLogbook(flights, wings)()

                // then
                assertThat(logbook.months.map { it.month }).containsExactly(YearMonth(2026, 10), YearMonth(2026, 9))
                assertThat(logbook.months[0].flights.map { it.id.value }).containsExactly("f-3", "f-2")
                assertThat(logbook.months[0].total).isEqualTo(75.minutes)
            }

        @Test
        fun `orders flights of the same day by take-off time, latest first`() =
            runTest {
                // given
                val day = LocalDate(2026, 10, 2)
                val morning =
                    aFlight(
                        "f-1",
                        aFlightDetails(date = day, takeOffTime = LocalTime(10, 15)),
                        loggedAt = Instant.parse("2026-10-02T18:00:00Z"),
                    )
                val afternoon =
                    aFlight(
                        "f-2",
                        aFlightDetails(date = day, takeOffTime = LocalTime(14, 40)),
                        loggedAt = Instant.parse("2026-10-02T09:00:00Z"),
                    )
                givenFlights(morning, afternoon)

                // when
                val logbook = GetLogbook(flights, wings)()

                // then
                assertThat(
                    logbook.months
                        .single()
                        .flights
                        .map { it.id.value },
                ).containsExactly("f-2", "f-1")
            }

        @Test
        fun `lists flights of the same day without take-off time after the timed ones`() =
            runTest {
                // given
                val day = LocalDate(2026, 10, 2)
                val untimed =
                    aFlight(
                        "f-1",
                        aFlightDetails(date = day, takeOffTime = null),
                        loggedAt = Instant.parse("2026-10-02T18:00:00Z"),
                    )
                val timed =
                    aFlight(
                        "f-2",
                        aFlightDetails(date = day, takeOffTime = LocalTime(10, 15)),
                        loggedAt = Instant.parse("2026-10-02T09:00:00Z"),
                    )
                givenFlights(untimed, timed)

                // when
                val logbook = GetLogbook(flights, wings)()

                // then
                assertThat(
                    logbook.months
                        .single()
                        .flights
                        .map { it.id.value },
                ).containsExactly("f-2", "f-1")
            }

        @Test
        fun `orders flights of the same day without take-off time by when they were logged, latest first`() =
            runTest {
                // given
                val day = aFlightDetails(date = LocalDate(2026, 10, 2), takeOffTime = null)
                val morning = aFlight("f-1", day, loggedAt = Instant.parse("2026-10-02T09:00:00Z"))
                val evening = aFlight("f-2", day, loggedAt = Instant.parse("2026-10-02T18:00:00Z"))
                givenFlights(morning, evening)

                // when
                val logbook = GetLogbook(flights, wings)()

                // then
                assertThat(
                    logbook.months
                        .single()
                        .flights
                        .map { it.id.value },
                ).containsExactly("f-2", "f-1")
            }

        @Test
        fun `is empty when nothing was logged`() =
            runTest {
                // given
                givenFlights()

                // when
                val logbook = GetLogbook(flights, wings)()

                // then
                assertThat(logbook.isEmpty).isTrue()
            }
    }

    @Nested
    inner class HoursByWing {
        @Test
        fun `adds the flights of a wing to the hours flown before tracking`() =
            runTest {
                // given
                val first = aFlight("f-1", aFlightDetails(duration = 90.minutes, wingId = skyDancer.id))
                val second = aFlight("f-2", aFlightDetails(duration = 30.minutes, wingId = skyDancer.id))
                val other = aFlight("f-3", aFlightDetails(duration = 20.minutes, wingId = cloudSurfer.id))
                givenFlights(first, second, other)

                // when
                val logbook = GetLogbook(flights, wings)()

                // then
                assertThat(logbook.hoursByWing[skyDancer.id]).isEqualTo(42.hours + 120.minutes)
                assertThat(logbook.hoursByWing[cloudSurfer.id]).isEqualTo(20.minutes)
            }

        @Test
        fun `shows only the pre-tracking hours for a wing without flights`() =
            runTest {
                // given
                givenFlights()

                // when
                val logbook = GetLogbook(flights, wings)()

                // then
                assertThat(logbook.hoursByWing[skyDancer.id]).isEqualTo(42.hours)
            }
    }

    @Nested
    inner class Filtered {
        @Test
        fun `keeps only matching flights and drops months left without any`() =
            runTest {
                // given
                val september =
                    aFlight(
                        "f-1",
                        aFlightDetails(date = LocalDate(2026, 9, 20), wingId = skyDancer.id, duration = 60.minutes),
                    )
                val october =
                    aFlight(
                        "f-2",
                        aFlightDetails(date = LocalDate(2026, 10, 2), wingId = cloudSurfer.id, duration = 30.minutes),
                    )
                givenFlights(september, october)
                val logbook = GetLogbook(flights, wings)()

                // when
                val filtered = logbook.filtered(FlightFilter(text = "dancer"))

                // then
                assertThat(filtered.months.map { it.month }).containsExactly(YearMonth(2026, 9))
                assertThat(filtered.flightCount).isEqualTo(1)
                assertThat(filtered.total).isEqualTo(60.minutes)
            }

        @Test
        fun `keeps the lifetime hours of every wing`() =
            runTest {
                // given
                val flight = aFlight("f-1", aFlightDetails(duration = 90.minutes, wingId = skyDancer.id))
                givenFlights(flight)
                val logbook = GetLogbook(flights, wings)()

                // when
                val filtered = logbook.filtered(FlightFilter(text = "fiesch"))

                // then
                assertThat(filtered.isEmpty).isTrue()
                assertThat(filtered.hoursByWing).isEqualTo(logbook.hoursByWing)
            }
    }

    @Nested
    inner class Problems {
        @Test
        fun `reports unreadable files from both flights and wings`() =
            runTest {
                // given
                val brokenFlight = UnreadableRecord("flight-9", "bad json", quarantined = true)
                val brokenWing = UnreadableRecord("wing-9", "bad json", quarantined = true)
                coEvery { flights.findAll() } returns LoadResult(emptyList(), listOf(brokenFlight))
                coEvery { wings.findAll() } returns LoadResult(listOf(skyDancer), listOf(brokenWing))

                // when
                val logbook = GetLogbook(flights, wings)()

                // then
                assertThat(logbook.unreadable).containsExactly(brokenFlight, brokenWing)
            }
    }
}
