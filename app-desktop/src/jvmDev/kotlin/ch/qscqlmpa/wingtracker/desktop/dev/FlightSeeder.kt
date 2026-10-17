package ch.qscqlmpa.wingtracker.desktop.dev

import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.LogFlight
import ch.qscqlmpa.wingtracker.flights.igc.ImportIgcFlight
import ch.qscqlmpa.wingtracker.flights.igc.PrepareIgcImport
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import org.koin.core.Koin
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

private const val SEED = 42
private const val IGC_SAMPLES = 3
private const val IGC_START_SECOND = 11 * 3600
private const val IGC_FLIGHT_MINUTES = 75
private const val MIN_FLIGHT_MINUTES = 20
private const val MAX_FLIGHT_MINUTES = 150
private val SEASON = Month.APRIL..Month.SEPTEMBER

// Roughly where the sample sites are, so the tracks open on a real map region.
private val LAUNCH = 46.9 to 8.4

/** Logs a believable number of flights per wing: more in summer, none outside the time the wing was in use. */
class FlightSeeder(
    private val koin: Koin,
    private val today: LocalDate,
) {
    private val random = Random(SEED)
    private val logFlight = koin.get<LogFlight>()

    suspend fun seed(wings: List<SeededWing>): Int {
        var count = 0
        wings.forEach { seeded ->
            // Sample wings always have a date, so the seeded flights have a first day to start from.
            val first = checkNotNull(seeded.wing.details.inServiceDate)
            val last = seeded.retiredOn ?: today
            repeat(first.daysUntil(last) + 1) { offset ->
                val date = first.plus(offset, DateTimeUnit.DAY)
                if (flownOn(date)) {
                    logOne(seeded, date)
                    count++
                }
            }
        }
        return count
    }

    /** Imports a few recent tracks for the newest wing, so imported flights and the tracks folder are exercised. */
    suspend fun seedTracks(wing: SeededWing): Int {
        val prepare = koin.get<PrepareIgcImport>()
        val import = koin.get<ImportIgcFlight>()
        repeat(IGC_SAMPLES) { index ->
            val date = today.plus(-(index + 1) * 9, DateTimeUnit.DAY)
            val bytes = SampleFiles.igc(date.toJava(), IGC_START_SECOND, IGC_FLIGHT_MINUTES + index * 10, LAUNCH)
            val file = NewAttachment("sample-$date.igc", bytes)
            val draft = prepare(file)
            val details =
                FlightDetails(
                    date = draft.date,
                    duration = checkNotNull(draft.duration) { "The sample track should contain a flight" },
                    wingId = wing.wing.id,
                    takeOffSite = SAMPLE_SITES[index].first,
                    landingSite = SAMPLE_SITES[index].second,
                    takeOffTime = draft.takeOffTime,
                )
            import(details, file, draft.checksum)
        }
        return IGC_SAMPLES
    }

    private suspend fun logOne(
        seeded: SeededWing,
        date: LocalDate,
    ) {
        val site = SAMPLE_SITES[random.nextInt(SAMPLE_SITES.size)]
        val minutes = random.nextInt(MIN_FLIGHT_MINUTES, MAX_FLIGHT_MINUTES + 1)
        val note = if (random.nextInt(NOTE_ONE_IN) == 0) SAMPLE_NOTES[random.nextInt(SAMPLE_NOTES.size)] else null
        // Some flights stay without a time, as many paper logbooks have none, so both orderings of a day show up.
        val takeOffTime =
            if (random.nextInt(UNTIMED_ONE_IN) == 0) {
                null
            } else {
                LocalTime(random.nextInt(FIRST_TAKE_OFF_HOUR, LAST_TAKE_OFF_HOUR + 1), random.nextInt(MINUTES_PER_HOUR))
            }
        logFlight(FlightDetails(date, minutes.minutes, seeded.wing.id, site.first, site.second, note, takeOffTime))
    }

    // Flying days are rare in winter and a bit more than weekly in summer; weekends are likelier.
    private fun flownOn(date: LocalDate): Boolean {
        val weekend = date.dayOfWeek.ordinal >= WEEKEND_FIRST_ORDINAL
        val percent =
            (if (date.month in SEASON) SUMMER_PERCENT else WINTER_PERCENT) + (if (weekend) WEEKEND_BONUS else 0)
        return random.nextInt(PERCENT) < percent
    }

    private companion object {
        const val NOTE_ONE_IN = 6
        const val UNTIMED_ONE_IN = 4
        const val FIRST_TAKE_OFF_HOUR = 9
        const val LAST_TAKE_OFF_HOUR = 17
        const val MINUTES_PER_HOUR = 60
        const val WEEKEND_FIRST_ORDINAL = 5
        const val SUMMER_PERCENT = 6
        const val WINTER_PERCENT = 1
        const val WEEKEND_BONUS = 12
        const val PERCENT = 100
    }
}

private fun LocalDate.toJava(): java.time.LocalDate = java.time.LocalDate.of(year, month.ordinal + 1, day)
