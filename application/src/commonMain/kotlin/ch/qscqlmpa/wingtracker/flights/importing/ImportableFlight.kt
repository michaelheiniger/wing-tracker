package ch.qscqlmpa.wingtracker.flights.importing

import ch.qscqlmpa.wingtracker.flights.ExternalReference
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/** A wing as another source names it; the pairing of name and brand identifies it within that source. */
data class SourceWing(
    val name: String,
    val brand: String?,
)

/** A flight read from another source, waiting for the pilot to say which wing in the app it was flown on. */
interface ImportableFlight {
    val date: LocalDate
    val duration: Duration
    val wing: SourceWing

    // Set when the source identifies its flights, so importing again never duplicates them.
    val externalReference: ExternalReference?

    fun toDetails(wingId: WingId): FlightDetails
}

/** The flights still to import on one source wing, with the existing wing that most likely is the same one. */
data class WingGroup<F : ImportableFlight>(
    val wing: SourceWing,
    val flights: List<F>,
    val suggestedWingId: WingId?,
) {
    val firstDate: LocalDate get() = flights.minOf { it.date }
    val lastDate: LocalDate get() = flights.maxOf { it.date }
    val totalDuration: Duration get() = flights.fold(Duration.ZERO) { sum, flight -> sum + flight.duration }
}
