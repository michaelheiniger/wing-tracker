package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.flights.ExternalReference
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDateInFutureException
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.FlightRules
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.wing.RegisterWing
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Where the flights of one Syride wing go. */
sealed interface WingTarget {
    data class Existing(
        val wingId: WingId,
    ) : WingTarget

    // [key] names a wing to create, so several Syride wings can end up on the same new wing.
    data class New(
        val key: String,
    ) : WingTarget

    data object Skip : WingTarget
}

/** What a pilot says about a wing that does not exist yet; the rest is derived from the flights. */
data class NewWingSpec(
    val name: String,
    val manufacturer: String,
    val model: String,
)

enum class SyrideFailure { DateInFuture, SaveFailed }

data class FailedSyrideFlight(
    val flight: SyrideFlight,
    val failure: SyrideFailure,
    val detail: String? = null,
)

data class SyrideImportResult(
    val imported: Int,
    val createdWings: List<Wing>,
    val skipped: Int,
    val failed: List<FailedSyrideFlight>,
)

/**
 * Brings the chosen Syride flights in, creating the wings that were asked for. Flights that cannot be imported
 * (they break the usual flight rules) are reported and do not stop the others; importing again is safe because
 * every imported flight remembers its Syride id.
 */
@OptIn(ExperimentalUuidApi::class)
class ImportSyrideFlights(
    private val flights: FlightRepository,
    private val wings: WingRepository,
    private val clock: AppClock,
    private val registerWing: RegisterWing,
    // Injectable so tests get deterministic ids.
    private val newId: () -> FlightId = { FlightId(Uuid.random().toString()) },
) {
    private val rules = FlightRules(wings, clock)

    suspend operator fun invoke(
        selection: List<Pair<SyrideFlight, WingTarget>>,
        newWings: Map<String, NewWingSpec>,
    ): SyrideImportResult {
        val alreadyImported = importedIds()
        val chosen = selection.filter { (flight, target) -> target != WingTarget.Skip && flight.id !in alreadyImported }
        val created = createWings(chosen, newWings)
        val targets = chosen.associate { (flight, target) -> flight.id to wingIdOf(target, created) }
        val failed = mutableListOf<FailedSyrideFlight>()
        var imported = 0
        // Oldest first: flights of one day are ordered by when they were logged, so this keeps their real order.
        chosen.sortedWith(compareBy({ it.first.date }, { it.first.startTime })).forEachIndexed { index, (flight, _) ->
            val failure = import(flight, targets.getValue(flight.id), index)
            if (failure == null) imported++ else failed += failure
        }
        return SyrideImportResult(imported, created.values.map { it.second }, selection.size - chosen.size, failed)
    }

    private suspend fun importedIds(): Set<String> =
        flights
            .findAll()
            .items
            .mapNotNull { it.importedFrom }
            .filter { it.system == SYRIDE_SYSTEM }
            .map { it.id }
            .toSet()

    private suspend fun createWings(
        chosen: List<Pair<SyrideFlight, WingTarget>>,
        newWings: Map<String, NewWingSpec>,
    ): Map<String, Pair<NewWingSpec, Wing>> {
        val flightsByKey =
            chosen
                .filter {
                    it.second is WingTarget.New
                }.groupBy({ (it.second as WingTarget.New).key }, { it.first })
        // A wing nobody flew on in this selection is not created.
        return flightsByKey.mapValues { (key, list) ->
            val spec = newWings.getValue(key)
            val details =
                WingDetails(
                    name = spec.name,
                    manufacturer = spec.manufacturer,
                    model = spec.model,
                    acquisition = Acquisition(AcquisitionCondition.New),
                    // Flights cannot predate the wing, so the wing starts with its first imported flight.
                    inServiceDate = list.minOf { it.date },
                )
            spec to registerWing(details)
        }
    }

    private fun wingIdOf(
        target: WingTarget,
        created: Map<String, Pair<NewWingSpec, Wing>>,
    ): WingId =
        when (target) {
            is WingTarget.Existing -> target.wingId
            is WingTarget.New -> created.getValue(target.key).second.id
            WingTarget.Skip -> error("Skipped flights are not imported")
        }

    private suspend fun import(
        flight: SyrideFlight,
        wingId: WingId,
        index: Int,
    ): FailedSyrideFlight? {
        val details =
            FlightDetails(
                date = flight.date,
                duration = flight.duration,
                wingId = wingId,
                takeOffSite = flight.siteName,
                notes = flight.name,
                distanceKm = flight.distanceKm,
                topAltitudeMeters = flight.topAltitudeMeters,
                instrument = flight.instrument,
            )
        return try {
            rules.ensureAllowed(details)
            // A millisecond apart, so the logbook keeps the order of the day instead of showing a tie.
            val loggedAt = clock.now() + index.milliseconds
            flights.save(Flight(newId(), details, loggedAt, importedFrom = ExternalReference(SYRIDE_SYSTEM, flight.id)))
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: FlightDateInFutureException) {
            FailedSyrideFlight(flight, SyrideFailure.DateInFuture)
            // A storage failure on one flight is reported on that flight; the others still go through.
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            FailedSyrideFlight(flight, SyrideFailure.SaveFailed, e.message)
        }
    }
}
