package ch.qscqlmpa.wingtracker.statistics

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightFilter
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

class GetStatistics(
    private val flights: FlightRepository,
    private val wings: WingRepository,
    private val clock: AppClock,
) {
    /**
     * With an active [filter], only the matching flights count: hours and flights before tracking are left out, as
     * they match no search, and so are the checks, which are about the wing rather than the flights.
     */
    suspend operator fun invoke(filter: FlightFilter = FlightFilter()): Statistics {
        val loadedFlights = flights.findAll()
        val loadedWings = wings.findAll()
        val today = clock.today()
        // Charts never reach into the future, and stop where a search ends.
        val until = filter.to?.let { minOf(it, today) } ?: today
        val names = loadedWings.items.associate { it.id to it.details.name }
        val counted = loadedFlights.items.filter { filter.matches(it, names[it.details.wingId]) }
        val hours = if (filter.isActive) HOURS.withoutBefore() else HOURS
        val counts = if (filter.isActive) COUNTS.withoutBefore() else COUNTS

        val ordered =
            loadedWings.items.sortedWith(
                // Wings without an in-service date come last, as the oldest series should stay first.
                compareBy<Wing, LocalDate?>(nullsLast()) { wing -> wing.details.inServiceDate }
                    .thenBy { wing -> wing.details.name }
                    .thenBy { wing -> wing.id.value },
            )
        val named = ordered.take(MAX_NAMED_SERIES)
        val folded = ordered.drop(MAX_NAMED_SERIES)
        val series =
            named.map { Series(SeriesKey.OfWing(it.id), listOf(it)) } +
                if (folded.isEmpty()) emptyList() else listOf(Series(SeriesKey.Other, folded))

        val flightsByWing = counted.groupBy { it.details.wingId }
        val window = Window(filter, until)
        val hoursByWing =
            ordered.associate { it.id to stepsOf(it, flightsByWing[it.id].orEmpty(), window, hours) }
        val countsByWing =
            ordered.associate { it.id to stepsOf(it, flightsByWing[it.id].orEmpty(), window, counts) }

        val keyOfWing =
            series.flatMap { s -> s.wings.map { it.id to s.key } }.toMap()
        val yearly = yearlyOf(counted, keyOfWing)
        val yearlyFlights = yearlyFlightsOf(counted, keyOfWing)
        return Statistics(
            series = series,
            shownSeries =
                if (filter.isActive) {
                    series.filter { s -> s.wings.any { it.id in flightsByWing } }
                } else {
                    series
                },
            yearly = yearly,
            cumulative =
                series.associate { it.key to it.cumulativeIn(hoursByWing, hours) }.mapValues { (_, steps) ->
                    steps.map { (date, total) -> CumulativePoint(date, total) }
                },
            yearlyFlights = yearlyFlights,
            cumulativeFlights =
                series.associate { it.key to it.cumulativeIn(countsByWing, counts) }.mapValues { (_, steps) ->
                    steps.map { (date, total) -> CumulativeFlightsPoint(date, total) }
                },
            sinceLastCheck = if (filter.isActive) emptyList() else sinceLastCheckOf(ordered, flightsByWing),
            totals = totalsOf(yearly, yearlyFlights, ordered, counting = !filter.isActive),
            until = until,
            unreadable = loadedFlights.unreadable + loadedWings.unreadable,
        )
    }

    private fun <T> Series.cumulativeIn(
        stepsByWing: Map<WingId, List<Step<T>>>,
        measure: Measure<T>,
    ): List<Step<T>> =
        if (key == SeriesKey.Other) {
            sumOfSteps(wings.map { stepsByWing.getValue(it.id) }, measure)
        } else {
            stepsByWing.getValue(wings.single().id)
        }

    private fun yearlyOf(
        allFlights: List<Flight>,
        keyOfWing: Map<WingId, SeriesKey>,
    ): List<YearlyHours> {
        // A flight on a wing that no longer loads has no series to show up in.
        val known = allFlights.filter { it.details.wingId in keyOfWing }
        if (known.isEmpty()) return emptyList()
        val byYear = known.groupBy { it.details.date.year }
        // Gap years are kept so the time axis stays honest about years without flying.
        return (byYear.keys.min()..byYear.keys.max()).map { year ->
            val hours =
                byYear[year]
                    .orEmpty()
                    .groupBy { keyOfWing.getValue(it.details.wingId) }
                    .mapValues { (_, list) -> list.fold(Duration.ZERO) { sum, f -> sum + f.details.duration } }
            YearlyHours(year, hours)
        }
    }

    private fun yearlyFlightsOf(
        allFlights: List<Flight>,
        keyOfWing: Map<WingId, SeriesKey>,
    ): List<YearlyFlights> {
        val known = allFlights.filter { it.details.wingId in keyOfWing }
        if (known.isEmpty()) return emptyList()
        val byYear = known.groupBy { it.details.date.year }
        return (byYear.keys.min()..byYear.keys.max()).map { year ->
            YearlyFlights(
                year,
                byYear[year].orEmpty().groupingBy { keyOfWing.getValue(it.details.wingId) }.eachCount(),
            )
        }
    }

    // Built from the yearly figures, which cover the same years, so the totals always match the charts' sums.
    private fun totalsOf(
        yearly: List<YearlyHours>,
        yearlyFlights: List<YearlyFlights>,
        wings: List<Wing>,
        counting: Boolean,
    ) = Totals(
        years =
            yearly.zip(yearlyFlights) { hours, flights ->
                YearTotal(
                    hours.year,
                    flights.counts.values.sum(),
                    hours.hours.values.fold(Duration.ZERO, Duration::plus),
                )
            },
        flightsBeforeTracking = if (counting) wings.sumOf { it.details.flightsBeforeTracking } else 0,
        hoursBeforeTracking =
            if (counting) {
                wings.fold(
                    Duration.ZERO,
                ) { sum, wing -> sum + wing.details.hoursBeforeTracking }
            } else {
                Duration.ZERO
            },
    )

    // Retired wings are left out: nothing is left to check on them.
    private fun sinceLastCheckOf(
        wings: List<Wing>,
        flightsByWing: Map<WingId, List<Flight>>,
    ): List<SinceLastCheck> =
        wings.filter { it.isActive }.map { wing ->
            // Any check counts, not only those restarting the maintenance interval.
            val check = wing.checks.maxByOrNull { it.date }
            // Inclusive of the check day, as the maintenance status counts hours since a check.
            val since =
                flightsByWing[wing.id].orEmpty().filter { check != null && it.details.date >= check.date }
            SinceLastCheck(
                wing,
                check,
                since.size,
                since.fold(Duration.ZERO) { sum, f -> sum + f.details.duration },
            )
        }

    /** One running total, hours or flights, so both are built by the same code. */
    private class Measure<T>(
        val zero: T,
        val plus: (T, T) -> T,
        val of: (Flight) -> T,
        val before: (Wing) -> T,
    ) {
        fun withoutBefore() = Measure(zero, plus, of) { zero }
    }

    /** The stretch of time the lines cover. */
    private class Window(
        val filter: FlightFilter,
        val until: LocalDate,
    )

    private data class Step<T>(
        val date: LocalDate,
        val total: T,
    )

    private fun <T> stepsOf(
        wing: Wing,
        wingFlights: List<Flight>,
        window: Window,
        measure: Measure<T>,
    ): List<Step<T>> {
        val firstFlight = wingFlights.minOfOrNull { it.details.date }
        // A search draws only the wings it found flights for, from where the search starts.
        if (window.filter.isActive && firstFlight == null) return emptyList()
        // Flights can predate the in-service date, and a wing may have no such date, so the line starts at whichever
        // comes first; a wing with neither starts today.
        val start =
            if (window.filter.isActive) {
                window.filter.from ?: firstFlight
            } else {
                listOfNotNull(wing.details.inServiceDate, firstFlight).minOrNull()
            } ?: window.until
        val steps = mutableListOf(Step(start, measure.before(wing)))
        var running = measure.before(wing)
        wingFlights.groupBy { it.details.date }.entries.sortedBy { it.key }.forEach { (date, sameDay) ->
            running = sameDay.fold(running) { sum, f -> measure.plus(sum, measure.of(f)) }
            // A flight on the first day updates the starting point instead of duplicating the date.
            if (steps.last().date == date) {
                steps[steps.lastIndex] = Step(date, running)
            } else {
                steps += Step(date, running)
            }
        }
        // The line runs to today, or to retirement, so a wing's last stretch is visible as flat.
        val end = minOf((wing.status as? GearStatus.Retired)?.on ?: window.until, window.until)
        if (end > steps.last().date) steps += Step(end, running)
        return steps
    }

    private fun <T> sumOfSteps(
        lines: List<List<Step<T>>>,
        measure: Measure<T>,
    ): List<Step<T>> {
        val dates = lines.flatMap { line -> line.map { it.date } }.distinct().sorted()
        return dates.map { date ->
            val total =
                lines.fold(measure.zero) { sum, line ->
                    measure.plus(sum, line.lastOrNull { it.date <= date }?.total ?: measure.zero)
                }
            Step(date, total)
        }
    }

    private companion object {
        val HOURS =
            Measure(Duration.ZERO, { a, b -> a + b }, { it.details.duration }, { it.details.hoursBeforeTracking })
        val COUNTS = Measure(0, { a, b -> a + b }, { 1 }, { it.details.flightsBeforeTracking })
    }
}
