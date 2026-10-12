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
        val cumulativeByWing =
            ordered.associate { it.id to cumulativeOf(it, flightsByWing[it.id].orEmpty(), today) }
        val cumulative =
            series.associate { s ->
                s.key to
                    if (s.key == SeriesKey.Other) {
                        sumOfSteps(s.wings.map { cumulativeByWing.getValue(it.id) })
                    } else {
                        cumulativeByWing.getValue(s.wings.single().id)
                    }
            }

        val keyOfWing =
            series.flatMap { s -> s.wings.map { it.id to s.key } }.toMap()
        return Statistics(
            series = series,
            yearly = yearlyOf(loadedFlights.items, keyOfWing),
            cumulative = cumulative,
            today = today,
            unreadable = loadedFlights.unreadable + loadedWings.unreadable,
        )
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

    private fun cumulativeOf(
        wing: Wing,
        wingFlights: List<Flight>,
        today: LocalDate,
    ): List<CumulativePoint> {
        // Flights can predate the in-service date, and a wing may have no such date, so the line starts at whichever
        // comes first; a wing with neither starts today.
        val start =
            listOfNotNull(wing.details.inServiceDate, wingFlights.minOfOrNull { it.details.date }).minOrNull() ?: today
        val points = mutableListOf(CumulativePoint(start, wing.details.hoursBeforeTracking))
        var running = wing.details.hoursBeforeTracking
        wingFlights.groupBy { it.details.date }.entries.sortedBy { it.key }.forEach { (date, sameDay) ->
            running += sameDay.fold(Duration.ZERO) { sum, f -> sum + f.details.duration }
            // A flight on the first day updates the starting point instead of duplicating the date.
            if (points.last().date == date) {
                points[points.lastIndex] = CumulativePoint(date, running)
            } else {
                points += CumulativePoint(date, running)
            }
        }
        // The line runs to today, or to retirement, so a wing's last stretch is visible as flat.
        val end = (wing.status as? GearStatus.Retired)?.on ?: today
        if (end > points.last().date) points += CumulativePoint(end, running)
        return points
    }

    private fun sumOfSteps(lines: List<List<CumulativePoint>>): List<CumulativePoint> {
        val dates = lines.flatMap { line -> line.map { it.date } }.distinct().sorted()
        return dates.map { date ->
            val total =
                lines.fold(Duration.ZERO) { sum, line ->
                    sum + (line.lastOrNull { it.date <= date }?.total ?: Duration.ZERO)
                }
            CumulativePoint(date, total)
        }
    }
}
