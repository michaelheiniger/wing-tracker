package ch.qscqlmpa.wingtracker.statistics

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.flights.Flight
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
    suspend operator fun invoke(): Statistics {
        val loadedFlights = flights.findAll()
        val loadedWings = wings.findAll()
        val today = clock.today()

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

        val flightsByWing = loadedFlights.items.groupBy { it.details.wingId }
        val hoursByWing =
            ordered.associate { it.id to stepsOf(it, flightsByWing[it.id].orEmpty(), today, HOURS) }
        val countsByWing =
            ordered.associate { it.id to stepsOf(it, flightsByWing[it.id].orEmpty(), today, COUNTS) }

        val keyOfWing =
            series.flatMap { s -> s.wings.map { it.id to s.key } }.toMap()
        return Statistics(
            series = series,
            yearly = yearlyOf(loadedFlights.items, keyOfWing),
            cumulative =
                series.associate { it.key to it.cumulativeIn(hoursByWing, HOURS) }.mapValues { (_, steps) ->
                    steps.map { (date, total) -> CumulativePoint(date, total) }
                },
            yearlyFlights = yearlyFlightsOf(loadedFlights.items, keyOfWing),
            cumulativeFlights =
                series.associate { it.key to it.cumulativeIn(countsByWing, COUNTS) }.mapValues { (_, steps) ->
                    steps.map { (date, total) -> CumulativeFlightsPoint(date, total) }
                },
            sinceLastCheck = sinceLastCheckOf(ordered, flightsByWing),
            today = today,
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
    )

    private data class Step<T>(
        val date: LocalDate,
        val total: T,
    )

    private fun <T> stepsOf(
        wing: Wing,
        wingFlights: List<Flight>,
        today: LocalDate,
        measure: Measure<T>,
    ): List<Step<T>> {
        // Flights can predate the in-service date, and a wing may have no such date, so the line starts at whichever
        // comes first; a wing with neither starts today.
        val start =
            listOfNotNull(wing.details.inServiceDate, wingFlights.minOfOrNull { it.details.date }).minOrNull() ?: today
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
        val end = (wing.status as? GearStatus.Retired)?.on ?: today
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
