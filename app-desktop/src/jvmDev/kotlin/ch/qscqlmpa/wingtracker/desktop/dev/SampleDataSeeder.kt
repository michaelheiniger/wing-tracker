package ch.qscqlmpa.wingtracker.desktop.dev

import kotlinx.datetime.LocalDate
import org.koin.core.Koin
import java.nio.file.Path

/** Creates the whole sample pilot history, in an order that keeps the real rules satisfied. */
class SampleDataSeeder(
    private val koin: Koin,
    // Kept for tests and messages: where the data ends up.
    private val directory: Path,
    private val today: LocalDate,
) {
    suspend fun seed(): String {
        val gear = GearSeeder(koin, today)
        val wings = gear.seedWings()
        gear.seedHarnesses()
        gear.seedReserves()
        val flights = FlightSeeder(koin, today)
        val logged = flights.seed(wings)
        val tracks = flights.seedTracks(wings.last { it.retiredOn == null })
        return "${wings.size} wings, ${SAMPLE_HARNESSES.size} harnesses, ${SAMPLE_RESERVES.size} reserves, " +
            "${logged + tracks} flights (of which $tracks from IGC tracks) in $directory"
    }
}
